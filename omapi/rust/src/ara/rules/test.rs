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

use anyhow::Result;
use log::trace;

use super::*;
use crate::tlv::{Object, Tag, Value};
use test_helpers::*;

fn init() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("se_ara_test")
            .with_max_level(log::LevelFilter::Trace)
            .with_log_buffer(android_logger::LogId::System)
            .format(|buf, record| {
                writeln!(
                    buf,
                    "{}:{} - {}",
                    record.file().unwrap_or("unknown"),
                    record.line().unwrap_or(0),
                    record.args()
                )
            }),
    );
}

#[test]
fn test_apdu_access_rule_merging() -> Result<()> {
    init();

    let values = [
        ApduAccessRule::Always,
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[0, 1, 2, 3, 4, 5, 6, 7])?).clone(),
        ApduAccessRule::Never,
    ];

    // For all combinations, check that merging produces a "greater" rule.
    for (a, b) in values.iter().cartesian_product(values.iter()) {
        let mut merged = a.clone();
        merged.merge(b.clone());

        assert!(merged >= *a);
        assert!(merged >= *b);
        if a < b {
            assert!(merged == *b)
        } else if b < a {
            assert!(merged == *a)
        } else {
            assert!(merged == *b);
            assert!(merged == *a);
        }
    }

    Ok(())
}

#[test]
fn test_apdu_access_rule_distinct_partial_allow_merging() -> Result<()> {
    init();

    // The merge of two distinct PartialAllows is different; it combines the filters.
    let mut rule =
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[0, 1, 2, 3, 4, 5, 6, 7])?);
    rule.merge(ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[7, 6, 5, 4, 3, 2, 1, 0])?));
    if let ApduAccessRule::PartialAllow(filter) = rule {
        assert!(
            filter == ApduFilterSet::from_data(&[7, 6, 5, 4, 3, 2, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7,])?
        )
    } else {
        panic!("Merger must be a PartialAllow");
    }

    Ok(())
}

#[test]
fn test_nfc_access_rule_merging() {
    init();

    let mut rule = NfcAccessRule::Always;
    rule.merge(NfcAccessRule::Never);
    assert!(rule == NfcAccessRule::Never);

    let mut rule = NfcAccessRule::Never;
    rule.merge(NfcAccessRule::Always);
    assert!(rule == NfcAccessRule::Never);

    let mut rule = NfcAccessRule::Never;
    rule.merge(NfcAccessRule::Never);
    assert!(rule == NfcAccessRule::Never);

    let mut rule = NfcAccessRule::Always;
    rule.merge(NfcAccessRule::Always);
    assert!(rule == NfcAccessRule::Always);
}

#[test]
fn test_apdu_filter() -> Result<()> {
    init();

    let reject_filter = ApduFilter::from_data(&[
        0x01, 0x02, 0x03, 0x04, // Allowed
        0x00, 0x00, 0x00, 0x00, // Mask
    ]);
    let accept_filter = ApduFilter::from_data(&[
        0x01, 0x02, 0x03, 0x04, // Allowed
        0xFF, 0xFF, 0xFF, 0xFF, // Mask
    ]);
    let almost_accept_filter = ApduFilter::from_data(&[
        0x01, 0x02, 0x03, 0x04, // Allowed
        0xFF, 0xFF, 0x01, 0xFF, // Mask
    ]);

    let test_apdu = [0x01, 0x02, 0x03, 0x04];

    assert!(!reject_filter.allow_apdu(&test_apdu));
    assert!(accept_filter.allow_apdu(&test_apdu));
    assert!(!almost_accept_filter.allow_apdu(&test_apdu));

    Ok(())
}

#[test]
fn test_apdu_filter_set() -> Result<()> {
    init();

    let filter_set = ApduFilterSet::from_data(&[
        0x01, 0x02, 0x03, 0x04, // Allowed 1
        0x00, 0x00, 0x00, 0x00, // Mask 1
        0x01, 0x02, 0x03, 0x04, // Allowed 2
        0xFF, 0xFF, 0xFF, 0xFF, // Mask 2
    ])?;

    // This test APDU should be rejected by first, allowed by second, so allowed.
    let test_apdu = [0x01, 0x02, 0x03, 0x04];
    assert!(filter_set.allow(&test_apdu));

    // To confirm:
    assert!(!filter_set.filters[0].allow_apdu(&test_apdu));
    assert!(filter_set.filters[1].allow_apdu(&test_apdu));

    // This test APDU should be allowed by neither, so rejected.
    let test_apdu = [0x02, 0x02, 0x03, 0x04];
    assert!(!filter_set.allow(&test_apdu));

    Ok(())
}

#[test]
fn test_invalid_apdu_filter_set() -> Result<()> {
    init();

    let filter_set = ApduFilterSet::from_data(&[0x01]);

    assert!(filter_set.is_err());
    if let Err(e) = filter_set {
        assert_eq!(e.to_string(), "Invalid data in ApduFilterSet [1]");
    }

    Ok(())
}

#[test]
fn test_invalid_access_rules() -> Result<()> {
    init();

    // No APDU or NFC
    assert!(AccessRules::from_tlv(&Object::new(Tag::ArDo, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Empty ArDo"));

    // Invalid entry tag
    let err = AccessRules::from_tlv(&Object::new(
        Tag::ArDo,
        Value::Constructed(vec![Object::new(Tag::ArDo, Value::Primitive(&[0x01]))]),
    ))
    .unwrap_err();
    assert!(err.to_string().contains("Invalid tag"));

    // Invalid outer content
    assert!(AccessRules::from_tlv(&Object::new(Tag::ArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Empty"));
    assert!(AccessRules::from_tlv(&Object::new(Tag::ArDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Primitive"));

    Ok(())
}

#[test]
fn test_invalid_apdu_rule() -> Result<()> {
    init();

    // Wrong content types
    assert!(ApduAccessRule::from_tlv(&Object::new(Tag::ApduArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Empty"));
    assert!(ApduAccessRule::from_tlv(&Object::new(Tag::ApduArDo, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Constructed"));

    // Can't be empty
    assert!(ApduAccessRule::from_tlv(&Object::new(Tag::ApduArDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("No data"));

    // Invalid single-byte value:
    assert!(ApduAccessRule::from_tlv(&Object::new(Tag::ApduArDo, Value::Primitive(&[2])))
        .unwrap_err()
        .to_string()
        .contains("Invalid data byte"));

    // Invalid filter
    assert!(ApduAccessRule::from_tlv(&Object::new(Tag::ApduArDo, Value::Primitive(&[2, 3])))
        .unwrap_err()
        .to_string()
        .contains("Invalid ApduFilters"));

    Ok(())
}

#[test]
fn test_invalid_nfc_rule() -> Result<()> {
    init();

    // Wrong content types
    assert!(NfcAccessRule::from_tlv(&Object::new(Tag::NfcArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Empty"));

    assert!(NfcAccessRule::from_tlv(&Object::new(Tag::NfcArDo, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Invalid content Constructed"));

    // Data must be a single byte
    assert!(NfcAccessRule::from_tlv(&Object::new(Tag::NfcArDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Invalid data"));
    assert!(NfcAccessRule::from_tlv(&Object::new(Tag::NfcArDo, Value::Primitive(&[1, 1])))
        .unwrap_err()
        .to_string()
        .contains("Invalid data"));

    // Invalid value (valid values are 0 and 1)
    assert!(NfcAccessRule::from_tlv(&Object::new(Tag::NfcArDo, Value::Primitive(&[3])))
        .unwrap_err()
        .to_string()
        .contains("Invalid data"));

    Ok(())
}

#[test]
fn test_invalid_device_app_ref() -> Result<()> {
    init();

    // Invalid content type
    assert!(DeviceAppIdRef::from_tlv(&Object::new(
        Tag::DeviceAppIdRefDo,
        Value::Constructed(vec![])
    ))
    .unwrap_err()
    .to_string()
    .contains("Invalid content Constructed"));

    // Invalid content data length (must be 20 or 32 bytes)
    assert!(DeviceAppIdRef::from_tlv(&Object::new(Tag::DeviceAppIdRefDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Invalid DeviceAppId content length"));
    assert!(DeviceAppIdRef::from_tlv(&Object::new(
        Tag::DeviceAppIdRefDo,
        Value::Primitive(&[1; 5])
    ))
    .unwrap_err()
    .to_string()
    .contains("Invalid DeviceAppId content length"));

    Ok(())
}

#[test]
fn test_invalid_match_criteria_structure() -> Result<()> {
    init();

    // Missing AID and app
    assert!(MatchCriteria::from_tlv(&Object::new(Tag::RefDo, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Missing AidRefDo"));

    // Missing app
    assert!(MatchCriteria::from_tlv(&Object::new(
        Tag::RefDo,
        Value::Constructed(vec![Object::new(Tag::AidRefDoImplicit, Value::Empty)]),
    ))
    .unwrap_err()
    .to_string()
    .contains("Missing DeviceAppIdRefDo"));

    // Extra entry
    assert!(MatchCriteria::from_tlv(&Object::new(
        Tag::RefDo,
        Value::Constructed(vec![
            Object::new(Tag::AidRefDoImplicit, Value::Empty),
            Object::new(Tag::DeviceAppIdRefDo, Value::Empty),
            Object::new(Tag::DeviceAppIdRefDo, Value::Empty)
        ]),
    ))
    .unwrap_err()
    .to_string()
    .contains("Found 3 components"));

    // Invalid content type
    let e = MatchCriteria::from_tlv(&Object::new(Tag::RefDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string();
    assert!(e.contains("Invalid RefDo content Primitive"), "{e}");
    assert!(MatchCriteria::from_tlv(&Object::new(Tag::RefDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Invalid RefDo content Empty"));

    Ok(())
}

#[test]
fn test_invalid_applet_ref() -> Result<()> {
    init();

    // Default applet ref with invalid content
    assert!(AppletRef::from_tlv(&Object::new(Tag::AidRefDoImplicit, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Unexpected content Primitive"));
    assert!(AppletRef::from_tlv(&Object::new(Tag::AidRefDoImplicit, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Unexpected content Constructed"));

    // AID ref with invalid content
    assert!(AppletRef::from_tlv(&Object::new(
        Tag::AidRefDoSpecificApplet,
        Value::Constructed(vec![])
    ))
    .unwrap_err()
    .to_string()
    .contains("Found invalid content"));

    // Wrong tag
    assert!(AppletRef::from_tlv(&Object::new(Tag::ArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Found unexpected tag"));

    Ok(())
}

#[test]
fn test_empty_rule_set() -> Result<()> {
    init();

    let tlv = Object::new(Tag::ResponseAllRefArDo, Value::Empty);

    let rule_set = RuleCache::from_tlv(&tlv)?;
    assert!(rule_set.rules.is_empty());

    assert_eq!(
        *rule_set
            .check_apdu_access(&DeviceAppId::new(vec![[0; 32]], vec![]), &AppletId::DefaultApplet),
        ApduAccessRule::Never
    );

    Ok(())
}
#[test]
fn test_access_rule_translation_nfc_defaults() -> Result<()> {
    init();

    // APDU NEVER, no NFC -> NFC NEVER
    assert_eq!(
        AccessRules::from_tlv(&access_rule(apdu_access_never(), no_rule()))?.nfc,
        NfcAccessRule::Never
    );

    // APDU ALWAYS, no NFC -> NFC ALWAYS
    assert_eq!(
        AccessRules::from_tlv(&access_rule(apdu_access_always(), no_rule()))?.nfc,
        NfcAccessRule::Always
    );

    // APDU FILTER, no NFC -> NFC ALWAYS
    assert_eq!(
        AccessRules::from_tlv(&access_rule(apdu_access_filtered(&[0; 8]), no_rule()))?.nfc,
        NfcAccessRule::Always
    );

    Ok(())
}

#[test]
fn test_access_rule_translation_apdu_defaults() -> Result<()> {
    init();

    // no APDU, NFC NEVER -> APDU NEVER
    assert_eq!(
        AccessRules::from_tlv(&access_rule(no_rule(), nfc_access_never()))?.apdu,
        ApduAccessRule::Never
    );

    // no APDU, NFC ALWAYS -> APDU NEVER
    assert_eq!(
        AccessRules::from_tlv(&access_rule(no_rule(), nfc_access_always()))?.apdu,
        ApduAccessRule::Never
    );

    Ok(())
}

#[test]
fn test_zen_rule_set() -> Result<()> {
    init();

    let app0_hash = &[0; 32];
    let app0 = &DeviceAppId { sha256: vec![*app0_hash], ..Default::default() };
    let app1_sha1_hash = &[1; 20];
    let app1_sha256_hash = &[1; 32];
    let app1 = &DeviceAppId { sha1_or_uuid: vec![*app1_sha1_hash], sha256: vec![*app1_sha256_hash] };
    let app2_hash = &[2; 32];
    let app2 = &DeviceAppId { sha256: vec![*app2_hash], ..Default::default() };
    let app3_hash = &[3; 32];
    let app3 = &DeviceAppId { sha256: vec![*app3_hash], ..Default::default() };

    let applet0 = &AppletId::Aid(&[0; 5]);
    let applet1 = &AppletId::Aid(&[1; 5]);
    let applet2 = &AppletId::Aid(&[2; 5]);
    let applet3 = &AppletId::Aid(&[3; 5]);
    let applet4 = &AppletId::Aid(&[4; 5]);
    let applet5 = &AppletId::Aid(&[5; 5]);

    let tlv_rule_set = rule_set(vec![
        // Allow all apps to access applet0
        rule(
            matcher(match_aid(applet0), match_any_app()),
            access_rule(apdu_access_always(), nfc_access_always()),
        ),
        // Allow app0 to access default-selected app.
        rule(
            matcher(match_default_applet(), match_app_sha256(app0_hash)),
            access_rule(apdu_access_always(), nfc_access_always()),
        ),
        // Allow app1 (specified with Sha1 hash) to get NFC events for applet1
        rule(
            matcher(match_aid(applet1), match_app_sha1(app1_sha1_hash)),
            access_rule(apdu_access_never(), nfc_access_always()),
        ),
        // Allow app1 (specified with Sha256 hash) to send INS 0xA0 to applet2
        rule(
            matcher(match_aid(applet2), match_app_sha256(app1_sha256_hash)),
            access_rule(
                apdu_access_filtered(&[
                    0x00, 0xA0, 0x00, 0x00, // allow
                    0x00, 0xFF, 0x00, 0x00, // mask
                ]),
                nfc_access_always(),
            ),
        ),
        // Another app1/applet2 rule, permissive on APDU and deny on NFC. This shouldn't change
        // the APDU filtering above (because filtering is stricter), but should change the NfC
        // always to never.
        rule(
            matcher(match_aid(applet2), match_app_sha256(app1_sha256_hash)),
            access_rule(apdu_access_always(), nfc_access_never()),
        ),
        // Allow app3 APDU access to everything, no NFC rule.
        rule(
            matcher(match_any_aid(), match_app_sha256(app3_hash)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        // Allow all apps to access applet4
        rule(
            matcher(match_aid(applet4), match_any_app()),
            access_rule(apdu_access_always(), nfc_access_always()),
        ),
        // But also define a rule for app0 access to applet4.  This "shadows" the previous
        // rule, preventing any app other than app0 from using applet4.
        rule(
            matcher(match_aid(applet4), match_app_sha256(app0_hash)),
            access_rule(apdu_access_always(), nfc_access_never()),
        ),
    ]);

    let rules = RuleCache::from_tlv(&tlv_rule_set)?;

    trace!("Zen RuleSet: {rules:#?}");

    // App2 has no special access, should be denied everything except applet0 (which is open-access).
    let test_app = &app2;
    assert_eq!(*rules.check_apdu_access(test_app, applet0), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet0));
    assert_eq!(*rules.check_apdu_access(test_app, applet1), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet1));
    assert_eq!(*rules.check_apdu_access(test_app, applet2), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet2));
    assert_eq!(*rules.check_apdu_access(test_app, applet3), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet3));
    assert_eq!(*rules.check_apdu_access(test_app, &AppletId::DefaultApplet), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, &AppletId::DefaultApplet));
    assert_eq!(*rules.check_apdu_access(test_app, applet4), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet4));
    assert_eq!(*rules.check_apdu_access(test_app, applet5), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet5));

    // App1 can also get applet1 NFC events and send INS 0xA0 APDUs to applet2
    let test_app = &app1;
    assert_eq!(*rules.check_apdu_access(test_app, applet0), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet0));
    assert_eq!(*rules.check_apdu_access(test_app, applet1), ApduAccessRule::Never);
    assert!(rules.check_nfc_access(test_app, applet1));
    if let ApduAccessRule::PartialAllow(filter) = rules.check_apdu_access(test_app, applet2) {
        assert!(filter.allow(&[0x00, 0xA0, 0x01, 0x05]));
        assert!(filter.allow(&[0x0F, 0xA0, 0x02, 0x09]));
        assert!(!filter.allow(&[0x00, 0xA1, 0x01, 0x05]));
    } else {
        panic!("APDU access for app1 and applet1 must have an APDU filter.")
    }
    assert!(!rules.check_nfc_access(test_app, applet2));
    assert_eq!(*rules.check_apdu_access(test_app, applet3), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet3));
    assert_eq!(*rules.check_apdu_access(test_app, &AppletId::DefaultApplet), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, &AppletId::DefaultApplet));
    assert_eq!(*rules.check_apdu_access(test_app, applet4), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet4));
    assert_eq!(*rules.check_apdu_access(test_app, applet5), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet5));

    // App0 has access to default-selected applet and applet4
    let test_app = &app0;
    assert_eq!(*rules.check_apdu_access(test_app, applet0), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet0));
    assert_eq!(*rules.check_apdu_access(test_app, applet1), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet1));
    assert_eq!(*rules.check_apdu_access(test_app, applet2), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet2));
    assert_eq!(*rules.check_apdu_access(test_app, applet3), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet3));
    assert_eq!(
        *rules.check_apdu_access(test_app, &AppletId::DefaultApplet),
        ApduAccessRule::Always
    );
    assert!(rules.check_nfc_access(test_app, &AppletId::DefaultApplet));
    assert_eq!(*rules.check_apdu_access(test_app, applet4), ApduAccessRule::Always);
    assert!(!rules.check_nfc_access(test_app, applet4));
    assert_eq!(*rules.check_apdu_access(test_app, applet5), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet5));

    // App3 has APDU access to everything; NFC access is default for the APDU ALWAYS case,
    // which per Annex G is ALWAYS.
    let test_app = &app3;
    assert_eq!(*rules.check_apdu_access(test_app, applet0), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet0));
    assert_eq!(*rules.check_apdu_access(test_app, applet1), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet1));
    assert_eq!(*rules.check_apdu_access(test_app, applet2), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet2));
    assert_eq!(*rules.check_apdu_access(test_app, applet3), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet3));
    assert_eq!(
        *rules.check_apdu_access(test_app, &AppletId::DefaultApplet),
        ApduAccessRule::Always
    );
    assert!(rules.check_nfc_access(test_app, &AppletId::DefaultApplet));
    assert_eq!(*rules.check_apdu_access(test_app, applet4), ApduAccessRule::Never);
    assert!(!rules.check_nfc_access(test_app, applet4));
    assert_eq!(*rules.check_apdu_access(test_app, applet5), ApduAccessRule::Always);
    assert!(rules.check_nfc_access(test_app, applet5));

    Ok(())
}

#[test]
fn test_highest_sha256_masks_sha1() -> Result<()> {
    init();

    let applet = AppletId::Aid(&[0; 5]);
    check_sha_256_masks_sha1(applet.clone(), match_aid(&applet))
}

#[test]
fn test_low_sha256_masks_sha1() -> Result<()> {
    init();

    let applet = AppletId::Aid(&[0; 5]);
    check_sha_256_masks_sha1(applet, match_any_aid())
}

fn check_sha_256_masks_sha1(
    applet: AppletId<'_>,
    applet_matcher: Object,
) -> std::result::Result<(), anyhow::Error> {
    let app = DeviceAppId::new(vec![[0; 32]], vec![[1; 20]]);

    // Define a couple of APDU "filters".  We won't use these to filter APDUs, they're just
    // handy ways to identify matched rules by which filter the rule contains.
    let filter0 = [0; 8];
    let filter1 = [1; 8];

    let sha1_rule_w_filter_0 = rule(
        matcher(applet_matcher.clone(), match_app_sha1(&app.sha1_or_uuid[0])),
        access_rule(apdu_access_filtered(&filter0), nfc_access_always()),
    );

    let sha256_rule_w_filter_1 = rule(
        matcher(applet_matcher.clone(), match_app_sha256(&app.sha256[0])),
        access_rule(apdu_access_filtered(&filter1), nfc_access_never()),
    );

    // With only the sha1 rule, we should get filter0 & NFC allowed
    let rules = RuleCache::from_tlv(&rule_set(vec![sha1_rule_w_filter_0.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter0)?)
    );
    assert!(rules.check_nfc_access(&app, &applet),);

    // With only the sha256 rule, we should get filter1 & NFC denied
    let rules = RuleCache::from_tlv(&rule_set(vec![sha256_rule_w_filter_1.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter1)?)
    );
    assert!(!rules.check_nfc_access(&app, &applet),);

    // With both sha1 and sha256 rules, we should again get the sha256 rule content.
    let rules = RuleCache::from_tlv(&rule_set(vec![
        sha256_rule_w_filter_1.clone(),
        sha1_rule_w_filter_0.clone(),
    ]))?;
    assert_eq!(
        *rules.check_apdu_access(&app, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter1)?)
    );
    assert!(!rules.check_nfc_access(&app, &applet),);

    // Rule order shouldn't matter
    let rules = RuleCache::from_tlv(&rule_set(vec![
        sha1_rule_w_filter_0.clone(),
        sha256_rule_w_filter_1.clone(),
    ]))?;
    assert_eq!(
        *rules.check_apdu_access(&app, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter1)?)
    );
    assert!(!rules.check_nfc_access(&app, &applet),);

    Ok(())
}

#[test]
fn test_low_rules_mask_least_rules() -> Result<()> {
    init();

    let app1 = DeviceAppId::new(vec![[0; 32]], vec![]);
    let app2 = DeviceAppId::new(vec![[1; 32]], vec![]);
    assert_ne!(app1, app2);

    // Define a couple of APDU "filters".  We won't use these to filter APDUs, they're just
    // handy ways to identify matched rules by which filter the rule contains.
    let filter0 = [0; 8];
    let filter1 = [1; 8];

    let low_rule = rule(
        matcher(match_any_aid(), match_app_sha256(&app1.sha256[0])),
        access_rule(apdu_access_filtered(&filter0), nfc_access_never()),
    );

    let least_rule = rule(
        matcher(match_any_aid(), match_any_app()),
        access_rule(apdu_access_filtered(&filter1), nfc_access_never()),
    );

    let applet = AppletId::Aid(&[0; 5]);

    // With only the low rule, we should get filter0 for app1 and NEVER for app2.
    let rules = RuleCache::from_tlv(&rule_set(vec![low_rule.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app1, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter0)?)
    );
    assert_eq!(*rules.check_apdu_access(&app2, &applet), ApduAccessRule::Never,);

    // With only the least rule, we should get filter1 for both apps.
    let rules = RuleCache::from_tlv(&rule_set(vec![least_rule.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app1, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter1)?)
    );
    assert_eq!(
        *rules.check_apdu_access(&app2, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter1)?)
    );

    // With both rules, we should get filter1 for app1, but NEVER for app2, because the low
    // rule masks the least rule, making it inapplicable.
    // With only the least rule, we should get filter1 for both apps.
    let rules = RuleCache::from_tlv(&rule_set(vec![least_rule.clone(), low_rule.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app1, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter0)?)
    );
    assert_eq!(*rules.check_apdu_access(&app2, &applet), ApduAccessRule::Never);

    // Rule order doesn't matter.
    let rules = RuleCache::from_tlv(&rule_set(vec![low_rule.clone(), least_rule.clone()]))?;
    assert_eq!(
        *rules.check_apdu_access(&app1, &applet),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&filter0)?)
    );
    assert_eq!(*rules.check_apdu_access(&app2, &applet), ApduAccessRule::Never);

    Ok(())
}

#[test]
fn test_invalid_rule_set() -> Result<()> {
    init();

    // Wrong tag
    assert!(RuleCache::from_tlv(&Object::new(Tag::ArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Found incorrect tag"));

    // Wrong content type
    assert!(RuleCache::from_tlv(&Object::new(Tag::ResponseAllRefArDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Invalid RuleCache content Primitive"));

    Ok(())
}

#[test]
fn test_invalid_rule() -> Result<()> {
    init();

    // Wrong content type
    assert!(Rule::from_tlv(&Object::new(Tag::RefArDo, Value::Empty))
        .unwrap_err()
        .to_string()
        .contains("Invalid RefArDo content Empty"));
    assert!(Rule::from_tlv(&Object::new(Tag::RefArDo, Value::Primitive(&[])))
        .unwrap_err()
        .to_string()
        .contains("Invalid RefArDo content Primitive"));

    // Missing match criteria
    assert!(Rule::from_tlv(&Object::new(Tag::RefArDo, Value::Constructed(vec![])))
        .unwrap_err()
        .to_string()
        .contains("Missing RefDo"));

    // Missing access rules
    assert!(Rule::from_tlv(&Object::new(
        Tag::RefArDo,
        Value::Constructed(vec![matcher(match_any_aid(), match_any_app())]),
    ))
    .unwrap_err()
    .to_string()
    .contains("Missing ArDo"));

    // Extra entry
    assert!(Rule::from_tlv(&Object::new(
        Tag::RefArDo,
        Value::Constructed(vec![
            matcher(match_any_aid(), match_any_aid()),
            access_rule(apdu_access_always(), nfc_access_always()),
            access_rule(apdu_access_always(), nfc_access_always()),
        ])
    ))
    .unwrap_err()
    .to_string()
    .contains("Found 3 components"));

    Ok(())
}

// Test case from page 106
#[test]
fn test_gp_doc_test_case_1() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![rule(
        matcher(match_aid(applet1), match_app(app1)),
        access_rule(apdu_access_always(), no_rule()),
    )]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    // Doc doesn't say what NFC access should be.

    Ok(())
}

// Test case from page 106
#[test]
fn test_gp_doc_test_case_2() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_app(app2)),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 106
#[test]
fn test_gp_doc_test_case_3() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![rule(
        matcher(match_aid(applet1), match_app(app1)),
        access_rule(apdu_access_never(), no_rule()),
    )]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 106
#[test]
fn test_gp_doc_test_case_4() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 106
#[test]
fn test_gp_doc_test_case_5() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![rule(
        matcher(match_aid(applet1), match_app(app1)),
        access_rule(apdu_access_filtered(&[1; 8]), no_rule()),
    )]))?;
    trace!("{rules:?}");

    assert_eq!(
        *rules.check_apdu_access(app1, applet1),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[1; 8])?)
    );
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 106
#[test]
fn test_gp_doc_test_case_6() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let applet3 = &AppletId::Aid(&[3]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app2)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_app(app2)),
            access_rule(apdu_access_filtered(&[1; 8]), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_app(app2)),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:#?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(
        *rules.check_apdu_access(app2, applet2),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[1; 8])?)
    );
    assert_eq!(*rules.check_apdu_access(app1, applet3), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet3), ApduAccessRule::Always);

    Ok(())
}

// Test case on page 106
#[test]
fn test_gp_doc_test_case_7() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_app(app2)),
            access_rule(apdu_access_filtered(&[1; 8]), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(
        *rules.check_apdu_access(app2, applet1),
        ApduAccessRule::PartialAllow(ApduFilterSet::from_data(&[1; 8])?)
    );
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_9() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_never(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_10() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_11() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_never(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_12() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_13() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![rule(
        matcher(match_any_aid(), match_any_app()),
        access_rule(apdu_access_always(), no_rule()),
    )]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Always);

    Ok(())
}

// Test case on page 107
#[test]
fn test_gp_doc_test_case_14() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 108
#[test]
fn test_gp_doc_test_case_15() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let applet3 = &AppletId::Aid(&[3]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_any_app()),
            access_rule(apdu_access_never(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app1, applet3), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet3), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 108
#[test]
fn test_gp_doc_test_case_16() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let applet3 = &AppletId::Aid(&[3]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_any_app()),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app1, applet3), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet3), ApduAccessRule::Never);

    Ok(())
}

// Test case on page 108
#[test]
fn test_gp_doc_test_case_17() -> Result<()> {
    init();

    let applet1 = &AppletId::Aid(&[1]);
    let applet2 = &AppletId::Aid(&[2]);
    let applet3 = &AppletId::Aid(&[3]);
    let app1 = &DeviceAppId::new(vec![[1; 32]], vec![]);
    let app2 = &DeviceAppId::new(vec![[2; 32]], vec![]);
    let app3 = &DeviceAppId::new(vec![[3; 32]], vec![]);
    let app4 = &DeviceAppId::new(vec![[4; 32]], vec![]);

    let rules = RuleCache::from_tlv(&rule_set(vec![
        rule(
            matcher(match_aid(applet1), match_app(app1)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_app(app2)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_app(app3)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet1), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_app(app2)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_app(app3)),
            access_rule(apdu_access_never(), no_rule()),
        ),
        rule(
            matcher(match_aid(applet2), match_any_app()),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_app(app1)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_app(app2)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_app(app3)),
            access_rule(apdu_access_always(), no_rule()),
        ),
        rule(
            matcher(match_any_aid(), match_any_app()),
            access_rule(apdu_access_never(), no_rule()),
        ),
    ]))?;
    trace!("{rules:?}");

    assert_eq!(*rules.check_apdu_access(app1, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app2, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app3, applet1), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app4, applet1), ApduAccessRule::Never);

    assert_eq!(*rules.check_apdu_access(app1, applet2), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app3, applet2), ApduAccessRule::Never);
    assert_eq!(*rules.check_apdu_access(app4, applet2), ApduAccessRule::Never);

    assert_eq!(*rules.check_apdu_access(app1, applet3), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app2, applet3), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app3, applet3), ApduAccessRule::Always);
    assert_eq!(*rules.check_apdu_access(app4, applet3), ApduAccessRule::Never);

    Ok(())
}

mod test_helpers {
    use super::{
        tlv::{Object, Tag, Value},
        AppletId, DeviceAppId,
    };

    pub fn match_any_aid() -> Object<'static> {
        Object::new(Tag::AidRefDoSpecificApplet, Value::Empty)
    }

    pub fn match_aid<'a>(applet: &'a AppletId) -> Object<'a> {
        match applet {
            AppletId::Aid(aid) => Object::new(Tag::AidRefDoSpecificApplet, Value::Primitive(aid)),
            AppletId::DefaultApplet => Object::new(Tag::AidRefDoImplicit, Value::Empty),
        }
    }

    pub fn match_default_applet() -> Object<'static> {
        Object::new(Tag::AidRefDoImplicit, Value::Empty)
    }

    pub fn match_any_app() -> Object<'static> {
        Object::new(Tag::DeviceAppIdRefDo, Value::Empty)
    }

    pub fn match_app(app: &DeviceAppId) -> Object<'_> {
        match_app_sha256(&app.sha256[0])
    }

    pub fn match_app_sha256(hash: &[u8; 32]) -> Object<'_> {
        Object::new(Tag::DeviceAppIdRefDo, Value::Primitive(hash))
    }

    pub fn match_app_sha1(hash: &[u8; 20]) -> Object {
        Object::new(Tag::DeviceAppIdRefDo, Value::Primitive(hash))
    }

    pub fn matcher<'a>(aid_ref: Object<'a>, app_ref: Object<'a>) -> Object<'a> {
        Object::new(Tag::RefDo, Value::Constructed(vec![aid_ref, app_ref]))
    }

    pub fn apdu_access_never() -> Option<Object<'static>> {
        Some(Object::new(Tag::ApduArDo, Value::Primitive(&[0x00])))
    }

    pub fn apdu_access_filtered(filters: &[u8]) -> Option<Object> {
        Some(Object::new(Tag::ApduArDo, Value::Primitive(filters)))
    }

    pub fn apdu_access_always() -> Option<Object<'static>> {
        Some(Object::new(Tag::ApduArDo, Value::Primitive(&[0x01])))
    }

    pub fn nfc_access_never() -> Option<Object<'static>> {
        Some(Object::new(Tag::NfcArDo, Value::Primitive(&[0x00])))
    }

    pub fn nfc_access_always() -> Option<Object<'static>> {
        Some(Object::new(Tag::NfcArDo, Value::Primitive(&[0x01])))
    }

    pub fn no_rule<'a>() -> Option<Object<'a>> {
        None
    }

    pub fn access_rule<'a>(
        apdu_rule: Option<Object<'a>>,
        nfc_rule: Option<Object<'a>>,
    ) -> Object<'a> {
        let mut vec = Vec::new();
        if let Some(apdu_rule) = apdu_rule {
            vec.push(apdu_rule);
        }
        if let Some(nfc_rule) = nfc_rule {
            vec.push(nfc_rule);
        }

        Object::new(Tag::ArDo, Value::Constructed(vec))
    }

    pub fn rule<'a>(matcher: Object<'a>, access_rule: Object<'a>) -> Object<'a> {
        Object::new(Tag::RefArDo, Value::Constructed(vec![matcher, access_rule]))
    }

    pub fn rule_set(rules: Vec<Object<'_>>) -> Object<'_> {
        Object::new(Tag::ResponseAllRefArDo, Value::Constructed(rules))
    }
}
