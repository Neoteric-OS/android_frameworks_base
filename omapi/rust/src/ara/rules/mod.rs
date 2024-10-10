// Copyright 2024, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Implementation of a Global Platform Access Control Enforcer.  See
//! <https://globalplatform.org/wp-content/uploads/2024/08/GPD_SE_Access_Control_v1.1.0.10_PublicRvw.pdf>.
//! The comments in this file that reference page numbers are referring to pages in that document.

use std::cmp::Ordering;

use anyhow::{anyhow, bail, ensure, Context, Result};
use itertools::{izip, Itertools};
use log::debug;

use crate::tlv;

/// DeviceAppId identifes the client application that wishes to use the Secure element, as
/// specified by hashes of signing certificates or a UUID.  Because ARA rules only distinguish
/// hash type by length, and UUIDs and SHA-1 hashes are the same length, we put both SHA-1 hashes
/// and UUIDs into the same bucket -- essentially "20-byte binary identifiers".
#[derive(Default, PartialEq)]
pub struct DeviceAppId {
    sha256: Vec<[u8; 32]>,
    sha1_or_uuid: Vec<[u8; 20]>,
}

impl DeviceAppId {
    /// Construct a [`DeviceAppId`] containing the SHA1 and SHA256 hashes of the provided
    /// certificates and/or a UUID.
    pub fn new(sha256: Vec<[u8; 32]>, sha1_or_uuid: Vec<[u8; 20]>) -> Self {
        DeviceAppId { sha256, sha1_or_uuid }
    }
}

impl std::fmt::Debug for DeviceAppId {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            formatter,
            "DeviceAppId: {{ Sha1CertHashes: [{}], Sha256CertHashes [{}] }} ",
            self.sha1_or_uuid.iter().map(hex::encode).join(", "),
            self.sha256.iter().map(hex::encode).join(", ")
        )
    }
}

/// AppletId identifies a target applet, either by AID or by specifying the SE channel's default.
#[derive(PartialEq, Clone)]
pub enum AppletId<'a> {
    /// Specifies an applet by AID; contains the bytes of the AID value.
    Aid(&'a [u8]),
    /// Specifies that the default-selected applet is being used.
    DefaultApplet,
}

impl<'a> std::fmt::Debug for AppletId<'a> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "AppletId: ")?;
        match self {
            Self::Aid(aid) => write!(f, "AID({})", hex::encode(aid)),
            Self::DefaultApplet => write!(f, "Default"),
        }
    }
}

/// ARA rules are ranked by specificity, and are evaluated strictly in the order defined by this
/// enum, "Highest" first.
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum RuleSpecificity {
    Highest, // Specifies AID and device app
    High,    // Specifies AID, matches any device app
    Low,     // Matches any AID, specifies device app
    Least,   // Matches any AID and any device app
}

/// Collection of ARA rules, which can be queried to check access permissionss.
///
/// Corresponds to Response-ALL-REF-DO.  See page 35.
#[derive(Debug)]
pub struct RuleCache {
    rules: Vec<Rule>,
}

impl RuleCache {
    /// Creates [`RuleCache`] from the provided tlv Object.  Returns [`Err`] if the object does not
    /// contain a valid set of ARA rules.
    pub fn from_tlv(tlv: &tlv::Object) -> Result<Self> {
        check_tag(tlv, tlv::Tag::ResponseAllRefArDo)?;
        match tlv.content() {
            tlv::Value::Empty => Ok(RuleCache { rules: Vec::new() }),
            tlv::Value::Constructed(vec) => RuleCache::from_vec(vec),
            tlv::Value::Primitive(_) => {
                bail!("Invalid RuleCache content {}", tlv.content())
            }
        }
    }

    /// Returns the APDU access restrictions for the specified device app and AID.  To check the
    /// default-selected applet specify [`AppletId::DefaultApplet`] for `applet`.
    pub fn check_apdu_access(&self, app_id: &DeviceAppId, applet: &AppletId) -> &ApduAccessRule {
        debug!("Checking APDU access to {applet:?} for {app_id:?}");
        &self.check_access(app_id, applet).apdu
    }

    /// Returns the NFC access restrictions for the specified device app and AID.  To check the
    /// default-selected applet specify [`AppletId::DefaultApplet`] for `applet`.
    pub fn check_nfc_access(&self, app_id: &DeviceAppId, applet: &AppletId) -> bool {
        debug!("Checking NFC access to {applet:?} for {app_id:?}");
        self.check_access(app_id, applet).nfc == NfcAccessRule::Always
    }

    /// This method extracts the Rules from TLV objects and constructs a RuleCache, but it does some
    /// extra work to implement meta-rules that typically are applied at evaluation time, i.e.
    /// when trying to compute access for an AID and app.  By doing some extra work now, we can
    /// pre-compute the evaluation-time application of those meta-rules and implement them in the
    /// rule set so the work doesn't have to be done during evaluation.
    ///
    /// There are four ways we "pre-optimize" the rules:
    ///
    /// 1.  Access merging.  The specification requires that if multiple rules match a request,
    ///     the strictest of their access specifications win.  But because of the hierarchical way
    ///     the rules are evaluated, the only way multiple rules can match a request is if they
    ///     have the same match criteria, i.e. specify the same AID and App.  We handle this by
    ///     finding such "redundant" rules here, and pre-merging the access rule.  The result is
    ///     that there is only one rule for a given set of match criteria.
    /// 2.  AID shadowing.  The specification requires that if an app-wildcard rule (specific AID,
    ///     but any app) matches a request, but that there is some specific rule (specific AID and
    ///     app) that references the same AID, the engine should deny the request, on the grounds
    ///     that if someone bothered to write a rule for a specific AID/App pair, they don't want
    ///     some wildcard rule to override it.  We handle this by finding any app-wildcard rules
    ///     that are "shadowed" by specific rules and setting their access to NEVER.  So, when the
    ///     wildcard rule is found, it will return NEVER, as required by the meta-rule.
    /// 3.  App shadowing.  The specification requires that if a fully generic rule (any AID, any
    ///     app) matches a request, but there exists an AID wildcard rule (any AID, specific app)
    ///     that matches a different app, the engine must return NEVER.  But we note that since
    ///     AID wildcard rules are checked before fully generic rules, if we ever get to the fully
    ///     generic rule it's because the request app didn't match any of the AID wildcard rules,
    ///     so in any case where we evaluate a fully generic rule after examining AID wildcard
    ///     rules the engine must return NEVER.  The only way a fully-generic rule will ever
    ///     actually be used is if there are no AID wildcard rules.  We handle this by checking to
    ///     see if there are AID wildcard rules, and if so we delete any fully-generic rules
    ///     (actually, thanks to access merging, there can be only one).
    /// 4.  Priority hierarchy.  Rules must be applied in strict priority order (based on
    ///     specificity/genericity).  We handle this putting the rules in a map ordered by
    ///     [`MatchCriteria::cmp`], which is defined to produce the correct order.
    ///
    /// By applying these optimizations now, we don't have to bother with these meta-rules at
    /// evaluation time.  We can just apply the rules sequentially, taking the first match.
    fn from_vec(vec: &[tlv::Object]) -> Result<Self> {
        let mut rule_set = RuleCache { rules: Vec::new() };
        for (i, object) in vec.iter().enumerate() {
            rule_set.merge(Rule::from_tlv(object).with_context(|| format!("Invalid rule {i}"))?);
        }
        rule_set.handle_shadowed_applets();
        rule_set.handle_shadowed_apps();

        rule_set.rules.sort_unstable_by_key(|r| r.criteria.clone());

        Ok(rule_set)
    }

    /// Merge rule into the existing rule set.  This means adding it to the rules if no rule with
    /// the same [`MatchCriteria`] already exists, and merging the [`AccessRules`] otherwise.
    fn merge(&mut self, new_rule: Rule) {
        if let Some(rule) = self.rules.iter_mut().find(|r| r.criteria == new_rule.criteria) {
            rule.access.merge(new_rule.access);
        } else {
            self.rules.push(new_rule);
        }
    }

    /// Handle applet shadowing by updating [`RuleSpecificity::High`] rules, that reference an AID
    /// referenced in a [`RuleSpecificity::Highest`] rule, setting their [`AccessRules`] to
    /// [`NEVER_RULE`].
    fn handle_shadowed_applets(&mut self) {
        let mut shadowing_aids = Vec::new();
        for rule in self.rules.iter() {
            if rule.criteria.specificity() == RuleSpecificity::Highest {
                shadowing_aids.push(rule.criteria.aid_ref.clone());
            }
        }
        for rule in self.rules.iter_mut() {
            if (rule.criteria.specificity() == RuleSpecificity::High)
                && shadowing_aids.iter().contains(&rule.criteria.aid_ref)
            {
                rule.access = NEVER_RULE.clone();
            }
        }
    }

    /// Handle app shadowing by removing any [`RuleSpecificity::Least`] rule if any
    /// [`RuleSpecificity::Low`] rules exist.
    fn handle_shadowed_apps(&mut self) {
        if self.rules.iter().rev().any(|r| r.criteria.specificity() == RuleSpecificity::Low) {
            self.rules.retain(|r| r.criteria.specificity() != RuleSpecificity::Least);
        }
    }

    fn check_access(&self, app_id: &DeviceAppId, applet: &AppletId) -> &AccessRules {
        // Note that we could implement this with a series of map lookups (exact match, then
        // app-wildcard, then aid wildcard, then all wildcard), but in the common case iteration
        // will be more efficient, and it's simpler.
        for rule in self.rules.iter() {
            if rule.criteria.matches(app_id, applet) {
                return &rule.access;
            }
        }
        &NEVER_RULE
    }
}

/// An access rule, specifying the conditions of match and the access permissions/restrictions to
/// be applied.
///
/// Corresponds to REF-AR-DO.  See page 68.
#[derive(Debug)]
struct Rule {
    criteria: MatchCriteria,
    access: AccessRules,
}

impl Rule {
    /// Create a [`Rule`] from a [`tlv::Object`].  Returns [`Err`] if the object does not contain
    /// a valid REF-AR-DO.
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Rule> {
        check_tag(tlv_obj, tlv::Tag::RefArDo)?;
        match tlv_obj.content() {
            tlv::Value::Constructed(vec) => Self::from_vec(vec),
            _ => bail!("Invalid RefArDo content {}", tlv_obj.content()),
        }
    }

    /// Create a [`Rule`] from a vector containing a REF-DO and AR-DO [`tlv::Object`]s.  Returns
    /// [`Err`] if the objects are not well-formed.
    fn from_vec(vec: &[tlv::Object<'_>]) -> Result<Rule> {
        ensure!(vec.len() <= 2, "Found {} components in RefArDo, should be 2.", vec.len());
        let criteria =
            MatchCriteria::from_tlv(vec.first().ok_or_else(|| anyhow!("Missing RefDo"))?)
                .context("Invalid RefDo in RefArDo")?;
        let access = AccessRules::from_tlv(vec.get(1).ok_or_else(|| anyhow!("Missing ArDo"))?)
            .context("Invalid ArDo in RefArDo")?;
        Ok(Rule { criteria, access })
    }
}

/// Defines the conditions for access.
///
/// The order in which rules are applied depends on their match criteria.  This type implements
/// [`Ord`] so sorted rules are in the order the specification requires them to be evaluated.
///
/// Corresponds to REF-DO.  See page 68.
#[derive(Clone, Debug, PartialEq, Eq)]
struct MatchCriteria {
    aid_ref: AppletRef,
    app_ref: DeviceAppIdRef,
}

impl MatchCriteria {
    /// Create [`MatchCriteria`] by parsing [`tlv::Object`].
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Self> {
        check_tag(tlv_obj, tlv::Tag::RefDo)?;

        if let tlv::Value::Constructed(vec) = tlv_obj.content() {
            Self::from_vec(vec)
        } else {
            bail!("Invalid RefDo content {}", tlv_obj.content())
        }
    }

    /// Create [`MatchCriteria`] from a vector containing AID-REF-DO and DeviceId-REF-DO objects.
    fn from_vec(vec: &[tlv::Object]) -> Result<Self> {
        ensure!(vec.len() <= 2, "Found {} components in RefDo, must be 2.", vec.len());
        let aid_ref = AppletRef::from_tlv(vec.first().ok_or(anyhow!("Missing AidRefDo"))?)?;
        let app_ref =
            DeviceAppIdRef::from_tlv(vec.get(1).ok_or(anyhow!("Missing DeviceAppIdRefDo"))?)?;
        Ok(Self { aid_ref, app_ref })
    }

    /// Returns true iff these MatchCriteria match the supplied app_id and AID.
    fn matches(&self, app_id: &DeviceAppId, aid: &AppletId) -> bool {
        self.aid_ref.matches(aid) && self.app_ref.matches(app_id)
    }

    /// Calculate the specificity-derived priority of a rule from the booleans indicating whether
    /// the rule specifies a specific AID and/or [`super::DeviceAppId`].  See the priority table
    /// on page 27.
    fn specificity(&self) -> RuleSpecificity {
        match (
            self.aid_ref != AppletRef::AllApplets,
            self.app_ref != DeviceAppIdRef::AllApplications,
        ) {
            (true, true) => RuleSpecificity::Highest,
            (true, false) => RuleSpecificity::High,
            (false, true) => RuleSpecificity::Low,
            (false, false) => RuleSpecificity::Least,
        }
    }
}

impl PartialOrd for MatchCriteria {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for MatchCriteria {
    fn cmp(&self, other: &Self) -> Ordering {
        self.specificity()
            .cmp(&other.specificity())
            .then(self.app_ref.cmp(&other.app_ref))
            .then(self.aid_ref.cmp(&other.aid_ref))
    }
}

/// Defines the AID matching condition for a [`Rule`].
///
/// Corresponds to AID-REF-DO.  See page 66.
#[derive(PartialEq, Clone, Eq, PartialOrd, Ord)]
enum AppletRef {
    AllApplets,
    DefaultApplet,
    SpecificApplet(Vec<u8>),
}

impl AppletRef {
    /// Create a new instance from a [`tlv::Object`].
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Self> {
        match tlv_obj.tag() {
            tlv::Tag::AidRefDoSpecificApplet => match tlv_obj.content() {
                tlv::Value::Empty => Ok(Self::AllApplets),
                tlv::Value::Primitive(bytes) => Ok(Self::SpecificApplet(bytes.to_vec())),
                tlv::Value::Constructed(_) => bail!("Found invalid content in AidRefDo"),
            },
            tlv::Tag::AidRefDoImplicit => match tlv_obj.content() {
                tlv::Value::Empty => Ok(Self::DefaultApplet),
                _ => {
                    bail!("Unexpected content {} in default applet ref", tlv_obj.content());
                }
            },

            other => bail!("Found unexpected tag {other}, where AidRefDo expected."),
        }
    }

    /// Returns true iff the supplied [`AppletId`] matches self.
    fn matches(&self, applet_id: &AppletId) -> bool {
        match self {
            AppletRef::AllApplets => true,
            AppletRef::DefaultApplet => *applet_id == AppletId::DefaultApplet,
            AppletRef::SpecificApplet(aid) => *applet_id == AppletId::Aid(aid),
        }
    }
}

impl std::fmt::Debug for AppletRef {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::AllApplets => write!(f, "AllApplets"),
            Self::DefaultApplet => write!(f, "DefaultApplet"),
            Self::SpecificApplet(arg0) => write!(f, "SpecificApplet({})", hex::encode(arg0)),
        }
    }
}

/// Defines the device app ID matching condition for a [`Rule`].
///
/// Corresponds to DeviceAppID-REF-DO.  See page 67.
///
/// Note that the order of the first two variants is crucial.  Rules are ordered by MatchCriteria,
/// which are ordered first by priority, then by DeviceAppIdRef, specifically to ensure that
/// SHA-256 IDs are tested before SHA1/UUID IDs, so [`DeviceAppIdRef::Sha256`] instances must be
/// less than (come before in sorting order) [`DeviceAppIdRef::Sha1OrUuid`] instances.  The
/// relative position of [`DeviceAppIdRef::AllApplications`] instances doesn't matter.
#[derive(PartialEq, Clone, Eq, PartialOrd, Ord)]
enum DeviceAppIdRef {
    Sha256([u8; 32]),
    Sha1OrUuid([u8; 20]),
    AllApplications,
}

impl DeviceAppIdRef {
    /// Create a new instance from a [`tlv::Object`].
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Self> {
        check_tag(tlv_obj, tlv::Tag::DeviceAppIdRefDo)?;

        let app_id_ref = match tlv_obj.content() {
            tlv::Value::Empty => Self::AllApplications,
            tlv::Value::Primitive(data) => match data.len() {
                20 => Self::Sha1OrUuid((*data).try_into()?),
                32 => Self::Sha256((*data).try_into()?),
                _ => bail!("Invalid DeviceAppId content length"),
            },
            tlv::Value::Constructed(_) => {
                bail!("Invalid content {} found in DeviceAppIdRefDo", tlv_obj.content())
            }
        };

        Ok(app_id_ref)
    }

    /// Returns true iff the supplied [`DeviceAppId`] matches self.
    fn matches(&self, app_id: &DeviceAppId) -> bool {
        match self {
            DeviceAppIdRef::Sha256(sha256) => app_id.sha256.iter().contains(&sha256),
            DeviceAppIdRef::Sha1OrUuid(sha1) => app_id.sha1_or_uuid.iter().contains(&sha1),
            DeviceAppIdRef::AllApplications => true,
        }
    }
}

impl std::fmt::Debug for DeviceAppIdRef {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::AllApplications => write!(f, "AllApplications"),
            Self::Sha1OrUuid(arg0) => {
                write!(f, "Sha1CertificateHash: [{}] ", hex::encode(arg0))
            }
            Self::Sha256(arg0) => {
                write!(f, "Sha256CertificateHash: [{}] ", hex::encode(arg0))
            }
        }
    }
}

/// Defines the conditions for access.
///
/// Corresponds to AR-DO, see page 69.
#[derive(Debug, Clone)]
struct AccessRules {
    apdu: ApduAccessRule,
    nfc: NfcAccessRule,
}

// It's convenient to have a static NEVER_RULE, so we can return references to it.
static NEVER_RULE: AccessRules =
    AccessRules { apdu: ApduAccessRule::Never, nfc: NfcAccessRule::Never };

impl AccessRules {
    /// Create [`AccessRules`] from a [`tlv::Object`].
    fn from_tlv(tlv: &tlv::Object) -> Result<Self> {
        check_tag(tlv, tlv::Tag::ArDo)?;
        if let tlv::Value::Constructed(obj_vec) = tlv.content() {
            Self::from_vec(obj_vec)
        } else {
            bail!("Invalid content {} in ArDo", tlv.content());
        }
    }

    /// Create [`AccessRules`] from a vector of APDU-AR-DO and NFC-AR-DO objects.
    fn from_vec(obj_vec: &[tlv::Object<'_>]) -> Result<Self> {
        let mut apdu = None;
        let mut nfc = None;

        for object in obj_vec.iter() {
            match object.tag() {
                tlv::Tag::ApduArDo => {
                    ensure!(apdu.is_none(), "Found multiple ApduArDo instances in ArDo");
                    ensure!(nfc.is_none(), "Found ApduArDo instance after NfcArDo");
                    apdu =
                        Some(ApduAccessRule::from_tlv(object).context("Invalid ApduArDo in ArDo")?)
                }
                tlv::Tag::NfcArDo => {
                    ensure!(nfc.is_none(), "Found multiple NfcArDo instances in ArDo");
                    nfc = Some(NfcAccessRule::from_tlv(object).context("Invalid NfcArDo in ArDo")?)
                }
                _ => bail!("Invalid tag {} in ArDo content", object.tag()),
            }
        }

        let (apdu, nfc) = handle_partial_ardo(apdu, nfc)?;
        Ok(AccessRules { apdu, nfc })
    }

    fn merge(&mut self, other: Self) {
        self.apdu.merge(other.apdu);
        self.nfc.merge(other.nfc);
    }
}

fn handle_partial_ardo(
    apdu: Option<ApduAccessRule>,
    nfc: Option<NfcAccessRule>,
) -> Result<(ApduAccessRule, NfcAccessRule)> {
    // This translation table implements the logic described in Annex G, page 128.  It
    // handles the cases where one of APDU/NFC is not present in the rule.
    let (apdu, nfc) = match (apdu, nfc) {
        (None, None) => bail!("Empty ArDo.  It should be impossible to get here."),
        (None, Some(nfc)) => (ApduAccessRule::Never, nfc),
        (Some(apdu), None) => match apdu {
            ApduAccessRule::Never => (ApduAccessRule::Never, NfcAccessRule::Never),
            apdu => (apdu, NfcAccessRule::Always),
        },
        (Some(apdu), Some(nfc)) => (apdu, nfc),
    };
    Ok((apdu, nfc))
}

/// Defines access permissions for APDU access.
///
/// Corresponds to APDU-AR-DO (pg. 70).
#[derive(PartialEq, PartialOrd, Debug, Clone)]
pub enum ApduAccessRule {
    /// APDU access is always allowed.
    Always,
    /// APDU access is allowed only if it matches one of the APDU filters.
    PartialAllow(ApduFilterSet),
    /// APDU access is never allowed.
    Never,
}

impl ApduAccessRule {
    /// Create a new instance from a [`tlv::Object`].
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Self> {
        check_tag(tlv_obj, tlv::Tag::ApduArDo)?;
        match tlv_obj.content() {
            tlv::Value::Primitive(data) => Self::from_data(data),
            _ => bail!("Invalid content {} in ApduArDo", tlv_obj.content()),
        }
    }

    /// Create [`ApduAccessRule`] from a buffer containing the content of an APDU-AR-DO object.
    fn from_data(data: &[u8]) -> Result<ApduAccessRule> {
        ensure!(!data.is_empty(), "No data in ApduArDo");
        Ok(match data.len() {
            1 => match data[0] {
                0 => Self::Never,
                1 => Self::Always,
                _ => bail!("Invalid data byte {} in ApduArDo", data[0]),
            },
            _ => Self::PartialAllow(
                ApduFilterSet::from_data(data).context("Invalid ApduFilters in ApduArDo")?,
            ),
        })
    }

    /// Merge other into self, taking other value where stricter.
    fn merge(&mut self, other: Self) {
        match (self, other) {
            (ApduAccessRule::PartialAllow(set1), ApduAccessRule::PartialAllow(mut set2)) => {
                set1.filters.append(&mut set2.filters);
                set1.filters.sort_unstable();
                set1.filters.dedup();
            }
            (self_, other) => {
                if *self_ < other {
                    *self_ = other
                }
            }
        }
    }
}

/// Defines the set of allowed APDUs.  Call [`ApduFilterSet::allow`] to determine whether
/// a given APDU should be permitted.
#[derive(PartialEq, PartialOrd, Debug, Clone)]
pub struct ApduFilterSet {
    filters: Vec<ApduFilter>,
}

impl ApduFilterSet {
    /// Create new ApduFilterSet from provided data.  Will return Err if data buffer is empty or
    /// not a multiple of 8 bytes in length.
    fn from_data(data: &[u8]) -> Result<Self> {
        ensure!(!data.is_empty() && data.len() % 8 == 0, "Invalid data in ApduFilterSet {data:?}");
        let mut filters: Vec<ApduFilter> =
            data.chunks_exact(8).map(ApduFilter::from_data).collect();
        filters.sort_unstable(); // Ensure that filter sets with the same contents compare Equal.
        Ok(ApduFilterSet { filters })
    }

    /// Returns true if the specified APDU header meets the filter requirements and should be
    /// allowed to be sent to the SE.
    pub fn allow(&self, apdu_header: &[u8; 4]) -> bool {
        self.filters.iter().any(|filter| filter.allow_apdu(apdu_header))
    }
}

/// Defines the set of allowed APDUs.
///
/// Each of [`ApduFilter::allowed`] and [`ApduFilter::mask`] are four bytes, corresponding to the
/// CLA, INS, P1 and P2 bytes of an APDU.  [`ApduFilter::mask`] is bitwise ANDed with the APDU to
/// be tested and the result is compared with [`ApduFilter::allowed`].  If they match, the APDU is
/// allowed.
///
/// See definition of APDU-AR-DO (pg. 70).
#[derive(PartialEq, Clone, PartialOrd, Ord, Eq)]
struct ApduFilter {
    allowed: [u8; 4],
    mask: [u8; 4],
}

impl ApduFilter {
    /// Create new ApduFilter from provided data.  Will panic if data buffer is not 8 bytes.
    fn from_data(data: &[u8]) -> ApduFilter {
        let data: &[u8; 8] = data.try_into().expect("ApduFilter called with incorrect data length");
        ApduFilter { allowed: data[..4].try_into().unwrap(), mask: data[4..].try_into().unwrap() }
    }

    fn allow_apdu(&self, apdu: &[u8; 4]) -> bool {
        // Check if masked valus is allowed at each position (CLA, INS, P1, P2).
        izip!(apdu, self.mask, self.allowed).all(|(apdu, mask, allow)| apdu & mask == allow)
    }
}

impl std::fmt::Debug for ApduFilter {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "ApduFilter: allowed {} mask {}",
            hex::encode(self.allowed),
            hex::encode(self.mask)
        )
    }
}

/// Defines an NFC access rule.
///
/// Corresponds to NFC-AR-DO (pg. 71).
#[derive(Clone, Debug, PartialEq)]
pub enum NfcAccessRule {
    Always,
    Never,
}

impl NfcAccessRule {
    /// Create [`NfcAccessRule`] from a [`tlv::Object`].
    fn from_tlv(tlv_obj: &tlv::Object) -> Result<Self> {
        check_tag(tlv_obj, tlv::Tag::NfcArDo)?;
        match tlv_obj.content() {
            tlv::Value::Primitive(data) => Self::from_data(data),
            _ => bail!("Invalid content {} in NfcArDo", tlv_obj.content()),
        }
    }

    /// Create [`NfcAccessRule`] from a buffer containing the content of an NFC-AR-DO object.
    fn from_data(data: &[u8]) -> Result<Self> {
        ensure!(data.len() == 1, "Invalid data in NfcArDo {}", hex::encode(data));
        match data[0] {
            0 => Ok(Self::Never),
            1 => Ok(Self::Always),
            _ => bail!("Invalid data in NfcArDo{}", hex::encode(data)),
        }
    }

    /// Merge the other access rule into self, taking the more restrictive.
    fn merge(&mut self, other: Self) {
        match self {
            NfcAccessRule::Always => *self = other,
            NfcAccessRule::Never => {}
        }
    }
}

/// Verify that the [`tlv::Object`] tag is the expected value.
fn check_tag(tlv: &tlv::Object<'_>, expected: tlv::Tag) -> Result<()> {
    if *tlv.tag() != expected {
        bail!("Found incorrect tag {} instead of {}", tlv.tag(), expected);
    }
    Ok(())
}

#[cfg(test)]
mod test;
