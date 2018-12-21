/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.telephony;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.internal.util.ArrayUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Returned as the reason for a data connection failure as defined by modem and some local errors.
 * @hide
 */
public final class DataFailCause {
    /** There is no failure */
    public static final int NONE = 0;

    // This series of errors as specified by the standards
    // specified in ril.h
    /** Operator determined barring. */
    public static final int OPERATOR_BARRED = 0x08;                                  /* no retry */
    /** NAS signalling. */
    public static final int NAS_SIGNALLING = 0x0E;
    /** Logical Link Control (LLC) Sub Network Dependent Convergence Protocol (SNDCP). */
    public static final int LLC_SNDCP = 0x19;
    /** Insufficient resources. */
    public static final int INSUFFICIENT_RESOURCES = 0x1A;
    /** Missing or unknown APN. */
    public static final int MISSING_UNKNOWN_APN = 0x1B;                              /* no retry */
    /** Unknown Packet Data Protocol (PDP) address type. */
    public static final int UNKNOWN_PDP_ADDRESS_TYPE = 0x1C;                         /* no retry */
    /** User authentication. */
    public static final int USER_AUTHENTICATION = 0x1D;                              /* no retry */
    /** Activation rejected by Gateway GPRS Support Node (GGSN), Serving Gateway or PDN Gateway. */
    public static final int ACTIVATION_REJECT_GGSN = 0x1E;                           /* no retry */
    /** Activation rejected, unspecified. */
    public static final int ACTIVATION_REJECT_UNSPECIFIED = 0x1F;
    /** Service option not supported. */
    public static final int SERVICE_OPTION_NOT_SUPPORTED = 0x20;                     /* no retry */
    /** Requested service option not subscribed. */
    public static final int SERVICE_OPTION_NOT_SUBSCRIBED = 0x21;                    /* no retry */
    /** Service option temporarily out of order. */
    public static final int SERVICE_OPTION_OUT_OF_ORDER = 0x22;
    /** The Network Service Access Point Identifier (NSAPI) is in use. */
    public static final int NSAPI_IN_USE = 0x23;                                     /* no retry */
    /* possibly restart radio, based on config */
    /** Regular deactivation. */
    public static final int REGULAR_DEACTIVATION = 0x24;
    /** Quality of service (QoS) is not accepted. */
    public static final int QOS_NOT_ACCEPTED = 0x25;
    /** Network Failure. */
    public static final int NETWORK_FAILURE = 0x26;
    /** Universal Mobile Telecommunications System (UMTS) reactivation request. */
    public static final int UMTS_REACTIVATION_REQ = 0x27;
    /** Feature not supported. */
    public static final int FEATURE_NOT_SUPP = 0x28;
    /** Semantic error in the Traffic flow templates (TFT) operation. */
    public static final int TFT_SEMANTIC_ERROR = 0x29;
    /** Syntactical error in the Traffic flow templates (TFT) operation. */
    public static final int TFT_SYTAX_ERROR = 0x2A;
    /** Unknown Packet Data Protocol (PDP) context. */
    public static final int UNKNOWN_PDP_CONTEXT = 0x2B;
    /** Semantic errors in packet filter. */
    public static final int FILTER_SEMANTIC_ERROR = 0x2C;
    /** Syntactical errors in packet filter(s). */
    public static final int FILTER_SYTAX_ERROR = 0x2D;
    /** Packet Data Protocol (PDP) without active traffic flow template (TFT). */
    public static final int PDP_WITHOUT_ACTIVE_TFT = 0x2E;
    /** Packet Data Protocol (PDP) type IPv4 only allowed. */
    /**
     * UE requested to modify QoS parameters or the bearer control mode, which is not compatible
     * with the selected bearer control mode.
     */
    public static final int ACTIVATION_REJECTED_BCM_VIOLATION = 0x30;
    public static final int ONLY_IPV4_ALLOWED = 0x32;                                /* no retry */
    /** Packet Data Protocol (PDP) type IPv6 only allowed. */
    public static final int ONLY_IPV6_ALLOWED = 0x33;                                /* no retry */
    /** Single address bearers only allowed. */
    public static final int ONLY_SINGLE_BEARER_ALLOWED = 0x34;
    /** EPS Session Management (ESM) information is not received. */
    public static final int ESM_INFO_NOT_RECEIVED = 0x35;
    /** PDN connection does not exist. */
    public static final int PDN_CONN_DOES_NOT_EXIST = 0x36;
    /** Multiple connections to a same PDN is not allowed. */
    public static final int MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED = 0x37;
    /**
     * Network has already initiated the activation, modification, or deactivation of bearer
     * resources that was requested by the UE.
     */
    public static final int COLLISION_WITH_NW_INIT_REQ = 0x38;
    /**
     * Network supports IPv4v6 PDP type only. Non-IP type is not allowed. In LTE mode of operation,
     * this is a PDN throttling cause code, meaning the UE may throttle further requests to the
     * same APN.
     */
    public static final int ONLY_IPV4V6_ALLOWED = 0x39;                              /* no retry */
    /**
     * Network supports non-IP PDP type only. IPv4, IPv6 and IPv4v6 is not allowed. In LTE mode of
     * operation, this is a PDN throttling cause code, meaning the UE can throttle further requests
     * to the same APN.
     */
    public static final int ONLY_NON_IP_ALLOWED = 0x3A;                              /* no retry */
    /** QCI indicated in the UE request cannot be supported. */
    public static final int UNSUPPORTED_QCI_VALUE = 0x3B;
    /** Procedure requested by the UE was rejected because the bearer handling is not supported. */
    public static final int BEARER_HANDLING_NOT_SUPPORTED = 0x3C;
    /** Packet Data Protocol (PDP) */
    public static final int MAX_ACTIVE_PDP_CONTEXT_REACHED = 0x41;
    /** Unsupported APN in current public land mobile network (PLMN). */
    public static final int UNSUPPORTED_APN_IN_CURRENT_PLMN = 0x42;
    /** Invalid transaction id. */
    public static final int INVALID_TRANSACTION_ID = 0x51;
    /** Incorrect message semantic. */
    public static final int MESSAGE_INCORRECT_SEMANTIC = 0x5F;
    /** Invalid mandatory information. */
    public static final int INVALID_MANDATORY_INFO = 0x60;
    /** Unsupported message type. */
    public static final int MESSAGE_TYPE_UNSUPPORTED = 0x61;
    /** Message type uncompatible. */
    public static final int MSG_TYPE_NONCOMPATIBLE_STATE = 0x62;
    /** Unknown info element. */
    public static final int UNKNOWN_INFO_ELEMENT = 0x63;
    /** Conditional Information Element (IE) error. */
    public static final int CONDITIONAL_IE_ERROR = 0x64;
    /** Message and protocol state uncompatible. */
    public static final int MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE = 0x65;
    /** Protocol errors. */
    public static final int PROTOCOL_ERRORS = 0x6F;                                  /* no retry */
    /** APN type conflict. */
    public static final int APN_TYPE_CONFLICT = 0x70;
    /** Invalid Proxy-Call Session Control Function (P-CSCF) address. */
    public static final int INVALID_PCSCF_ADDR = 0x71;
    /** Internal data call preempt by high priority APN. */
    public static final int INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN = 0x72;
    /** EPS (Evolved Packet System) Mobility Management (EMM) access barred. */
    public static final int EMM_ACCESS_BARRED = 0x73;
    /** Emergency interface only. */
    public static final int EMERGENCY_IFACE_ONLY = 0x74;
    /** Interface mismatch. */
    public static final int IFACE_MISMATCH = 0x75;
    /** Companion interface in use. */
    public static final int COMPANION_IFACE_IN_USE = 0x76;
    /** IP address mismatch. */
    public static final int IP_ADDRESS_MISMATCH = 0x77;
    public static final int IFACE_AND_POL_FAMILY_MISMATCH = 0x78;
    /** EPS (Evolved Packet System) Mobility Management (EMM) access barred infinity retry. **/
    public static final int EMM_ACCESS_BARRED_INFINITE_RETRY = 0x79;
    /** Authentication failure on emergency call. */
    public static final int AUTH_FAILURE_ON_EMERGENCY_CALL = 0x7A;
    /** Not receiving a DNS address that was mandatory. */
    public static final int INVALID_DNS_ADDR = 0x7B;
    /** Not receiving either a PCSCF or a DNS address, one of them being mandatory. */
    public static final int INVALID_PCSCF_DNS_ADDR = 0x7C;
    /** Emergency call bring up on a different ePDG. */
    public static final int CALL_PREEMPT_BY_EMERGENCY_APN = 0x7F;
    /** UE performs a detach or disconnect PDN action based on TE requirements. */
    public static final int UE_INIT_DETACH_OR_DISCONNECT = 0x80;

    /** Reason unspecified for foreign agent rejected MIP registration. */
    public static final int MIP_FA_REASON_UNSPECIFIED = 0x7D0;                       /* no retry */
    /** Foreign agent administratively prohibited MIP registration. */
    public static final int MIP_FA_ADMIN_PROHIBITED = 0x7D1;                         /* no retry */
    /** Foreign agent rejected MIP registration because of insufficient resources. */
    public static final int MIP_FA_INSUFFICIENT_RESOURCES = 0x7D2;                   /* no retry */
    /** Foreign agent rejected MIP registration because of MN-AAA authenticator was wrong. */
    public static final int MIP_FA_MOBILE_NODE_AUTH_FAILURE = 0x7D3;                 /* no retry */
    /** Foreign agent rejected MIP registration because of home agent authentication failure. */
    public static final int MIP_FA_HA_AUTH_FAILURE = 0x7D4;                          /* no retry */
    /** Foreign agent rejected MIP registration because of requested lifetime was too long. */
    public static final int MIP_FA_REQ_LIFETIME_TOO_LONG = 0x7D5;
    /** Foreign agent rejected MIP registration because of malformed request. */
    public static final int MIP_FA_MALFORMED_REQUEST = 0x7D6;                        /* no retry */
    /** Foreign agent rejected MIP registration because of malformed reply. */
    public static final int MIP_FA_MALFORMED_REPLY = 0x7D7;                          /* no retry */
    /**
     * Foreign agent rejected MIP registration because of requested encapsulation was unavailable.
     */
    public static final int MIP_FA_ENCAPSULATION_UNAVAILABLE = 0x7D8;                /* no retry */
    /** Foreign agent rejected MIP registration of VJ Header Compression was unavailable. */
    public static final int MIP_FA_VJHC_UNAVAILABLE = 0x7D9;                         /* no retry */
    /**
     * Foreign agent rejected MIP registration because of reverse tunnel was unavailable.
     */
    public static final int MIP_FA_REV_TUNNEL_UNAVAILABLE = 0x7DA;                   /* no retry */
    /**
     * Foreign agent rejected MIP registration because of reverse tunnel was mandatory but not
     * requested by device.
     */
    public static final int MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET = 0x7DB;     /* no retry */
    /** Foreign agent rejected MIP registration because of delivery style was not supported. */
    public static final int MIP_FA_DELIVERY_STYLE_NOT_SUPP = 0x7DC;                  /* no retry */
    /** Foreign agent rejected MIP registration because of missing NAI. */
    public static final int MIP_FA_MISSING_NAI = 0x7DD;                              /* no retry */
    /** Foreign agent rejected MIP registration because of missing Home Agent. */
    public static final int MIP_FA_MISSING_HA = 0x7DE;                               /* no retry */
    /** Foreign agent rejected MIP registration because of missing Home Address. */
    public static final int MIP_FA_MISSING_HOME_ADDR = 0x7DF;                        /* no retry */
    /** Foreign agent rejected MIP registration because of unknown challenge. */
    public static final int MIP_FA_UNKNOWN_CHALLENGE = 0x7E0;
    /** Foreign agent rejected MIP registration because of missing challenge. */
    public static final int MIP_FA_MISSING_CHALLENGE = 0x7E1;                        /* no retry */
    /** Foreign agent rejected MIP registration because of stale challenge. */
    public static final int MIP_FA_STALE_CHALLENGE = 0x7E2;
    /** Reason unspecified for home agent rejected MIP registration. */
    public static final int MIP_HA_REASON_UNSPECIFIED = 0x7E3;                       /* no retry */
    /** Home agent administratively prohibited MIP registration. */
    public static final int MIP_HA_ADMIN_PROHIBITED = 0x7E4;                         /* no retry */
    /** Home agent rejected MIP registration because of insufficient resources. */
    public static final int MIP_HA_INSUFFICIENT_RESOURCES = 0x7E5;                   /* no retry */
    /** Home agent rejected MIP registration because of MN-HA authenticator was wrong. */
    public static final int MIP_HA_MOBILE_NODE_AUTH_FAILURE = 0x7E6;                 /* no retry */
    /** Home agent rejected MIP registration because of foreign agent authentication failure. */
    public static final int MIP_HA_FA_AUTH_FAILURE = 0x7E7;                          /* no retry */
    /** Home agent rejected MIP registration because of registration id mismatch. */
    public static final int MIP_HA_REGISTRATION_ID_MISMATCH = 0x7E8;
    /** Home agent rejected MIP registration because of malformed request. */
    public static final int MIP_HA_MALFORMED_REQUEST = 0x7E9;                        /* no retry */
    /** Home agent rejected MIP registration because of unknown home agent address. */
    public static final int MIP_HA_UNKNOWN_HA_ADDR = 0x7EA;                          /* no retry */
    /** Home agent rejected MIP registration because of reverse tunnel was unavailable. */
    public static final int MIP_HA_REV_TUNNEL_UNAVAILABLE = 0x7EB;                   /* no retry */
    /**
     * Home agent rejected MIP registration because of reverse tunnel is mandatory but not
     * requested by device. (no retry)
     */
    public static final int MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET = 0x7EC;
    /** Home agent rejected MIP registration because of encapsulation unavailable. */
    public static final int MIP_HA_ENCAPSULATION_UNAVAILABLE = 0x7ED;                /* no retry */
    /** Tearing down is in progress. */
    public static final int CLOSE_IN_PROGRESS = 0x7EE;
    /** Brought down by the network. */
    public static final int NW_INITIATED_TERMINATION = 0x7EF;                        /* no retry */
    /** Another application in modem preempts the data call. */
    public static final int MODEM_APP_PREEMPTED = 0x7F0;
    /**
     * V4 PDN is in throttled state due to network providing only V6 address during the previous
     * VSNCP bringup (subs_limited_to_v6).
     */
    public static final int ERR_PDN_IPV4_CALL_DISALLOWED = 0x7F1;
    /** V4 PDN is in throttled state due to previous VSNCP bringup failure(s). */
    public static final int ERR_PDN_IPV4_CALL_THROTTLED = 0x7F2;
    /**
     * V6 PDN is in throttled state due to network providing only V4 address during the previous
     * VSNCP bringup (subs_limited_to_v4).
     */
    public static final int ERR_PDN_IPV6_CALL_DISALLOWED = 0x7F3;
    /** V6 PDN is in throttled state due to previous VSNCP bringup failure(s). */
    public static final int ERR_PDN_IPV6_CALL_THROTTLED = 0x7F4;
    /** Modem restart. */
    public static final int MODEM_RESTART = 0x7F5;
    /** PDP PPP calls are not supported. */
    public static final int PDP_PPP_NOT_SUPPORTED = 0x7F6;
    /** RAT on which the data call is attempted/connected is no longer the preferred RAT. */
    public static final int UNPREFERRED_RAT = 0x7F7;
    /** Physical link is in the process of cleanup. */
    public static final int PHYS_LINK_CLOSE_IN_PROGRESS = 0x7F8;
    /** Interface bring up is attempted for an APN that is yet to be handed over to target RAT. */
    public static final int APN_PENDING_HANDOVER = 0x7F9;
    /** APN bearer type in the profile does not match preferred network mode. */
    public static final int PROFILE_BEARER_INCOMPATIBLE = 0x7FA;
    /** Card was refreshed or removed. */
    public static final int SIM_CARD_EVT = 0x7FB;
    /** Device is going into lower power mode or powering down. */
    public static final int LPM_OR_PWR_DOWN = 0x7FC;
    /** APN has been disabled. */
    public static final int APN_DISABLED = 0x7FD;
    /** Maximum PPP inactivity timer expired. */
    public static final int MAX_PPP_INACTIVITY_TIMER_EXPIRED = 0x7FE;
    /** IPv6 address transfer failed. */
    public static final int IPV6_ADDR_TRANSFER_FAILED = 0x7FF;
    /** Target RAT swap failed. */
    public static final int TRAT_SWAP_FAILED = 0x800;
    /** Device falls back from eHRPD to HRPD. */
    public static final int EHRPD_TO_HRPD_FALLBACK = 0x801;
    /**
     * UE is in MIP-only configuration but the MIP configuration fails on call bring up due to
     * incorrect provisioning.
     */
    public static final int MIP_CONFIG_FAILURE = 0x802;                              /* no retry */
    /**
     * PDN inactivity timer expired due to no data transmission in a configurable duration of time.
     */
    public static final int PDN_INACTIVITY_TIMER_EXPIRED = 0x803;
    /**
     * IPv4 data call bring up is rejected because the UE already maintains the allotted maximum
     * number of IPv4 data connections.
     */
    public static final int MAX_V4_CONNECTIONS = 0x804;
    /**
     * IPv6 data call bring up is rejected because the UE already maintains the allotted maximum
     * number of IPv6 data connections.
     */
    public static final int MAX_V6_CONNECTIONS = 0x805;
    /**
     * New PDN bring up is rejected during interface selection because the UE has already allotted
     * the available interfaces for other PDNs.
     */
    public static final int APN_MISMATCH = 0x806;
    /**
     * New call bring up is rejected since the existing data call IP type doesn't match the
     * requested IP.
     */
    public static final int IP_VERSION_MISMATCH = 0x807;
    /** Dial up networking (DUN) call bring up is rejected since UE is in eHRPD RAT. */
    public static final int DUN_CALL_DISALLOWED = 0x808;
    /*** Rejected/Brought down since UE is transition between EPC and NONEPC RAT. */
    public static final int INTERNAL_EPC_NONEPC_TRANSITION = 0x809;
    /** The current interface is being in use. */
    public static final int IFACE_IN_USE = 0x80A;
    /** PDN connection to the APN is disallowed on the roaming network. */
    public static final int APN_DISALLOWED_ON_ROAMING = 0x80C;
    /** APN-related parameters are changed. */
    public static final int APN_PARAM_CHANGED = 0x80D;
    /** PDN is attempted to be brought up with NULL APN but NULL APN is not supported. */
    public static final int NULL_APN_DISALLOWED = 0x80E;
    /**
     * Thermal level increases and causes calls to be torn down when normal mode of operation is
     * not allowed.
     */
    public static final int THERMAL_MITIGATION = 0x80F;
    /**
     * PDN Connection to a given APN is disallowed because data is disabled from the device user
     * interface settings.
     */
    public static final int DATA_SETTINGS_DISABLED = 0x810;
    /**
     * PDN Connection to a given APN is disallowed because data roaming is disabled from the device
     * user interface settings and the UE is roaming.
     */
    public static final int DATA_ROAMING_SETTINGS_DISABLED = 0x811;
    /** Default data subscription switch occurs. */
    public static final int DDS_CALL_ABORT = 0x812;
    /** PDN being brought up with an APN that is part of forbidden APN Name list. */
    public static final int INVALID_APN_NAME = 0x813;
    /** Default data subscription switch is in progress. */
    public static final int DDS_SWITCH_IN_PROGRESS = 0x814;
    /** Roaming is disallowed during call bring up. */
    public static final int CALL_DISALLOWED_IN_ROAMING = 0x815;
    /**
     * UE is unable to bring up a non-IP data call because the device is not camped on a NB1 cell.
     */
    public static final int NON_IP_NOT_SUPPORTED = 0x816;                            /* no retry */
    /** Non-IP PDN is in throttled state due to previous VSNCP bringup failure(s). */
    public static final int ERR_PDN_NON_IP_CALL_THROTTLED = 0x817;
    /** Non-IP PDN is in disallowed state due to the network providing only an IP address. */
    public static final int ERR_PDN_NON_IP_CALL_DISALLOWED = 0x818;
    /** Device in CDMA locked state. */
    public static final int CDMA_LOCK = 0x819;                                       /* no retry */
    /** Received an intercept order from the base station. */
    public static final int CDMA_INTERCEPT = 0x81A;
    /** Receiving a reorder from the base station. */
    public static final int CDMA_REORDER = 0x81B;
    /** Receiving a release from the base station with a SO Reject reason. */
    public static final int CDMA_REL_SO_REJ = 0x81C;                                 /* no retry */
    /** Receiving an incoming call from the base station. */
    public static final int CDMA_INCOM_CALL = 0x81D;
    /** RL/FL fade or receiving a call release from the base station. */
    public static final int CDMA_ALERT_STOP = 0x81E;
    /**
     * Channel acquisition failures. This indicates that device has failed acquiring all the
     * channels in the PRL.
     */
    public static final int CHANNEL_ACQUISITION_FAILURE = 0x81F;
    /** Maximum access probes transmitted. */
    public static final int MAX_ACCESS_PROBE = 0x820;
    /** Concurrent service is not supported by base station. */
    public static final int CCS_NOT_SUPPORTED_BY_BS = 0x821;                         /* no retry */
    /** There was no response received from the base station. */
    public static final int NO_RESPONSE_FROM_BS = 0x822;
    /** The base station rejecting the call. */
    public static final int REJECTED_BY_BS = 0x823;                                  /* no retry */
    /** The concurrent services requested were not compatible. */
    public static final int CCS_INCOMPATIBLE = 0x824;                                /* no retry */
    /** Device does not have CDMA service. */
    public static final int NO_CDMA_SRV = 0x825;
    /** RUIM not being present. */
    public static final int UIM_NOT_PRESENT = 0x826;                                 /* no retry */
    /** Receiving a retry order from the base station. */
    public static final int CDMA_RETRY_ORDER = 0x827;
    /** Access blocked by the base station. */
    public static final int ACCESS_BLOCK = 0x828;
    /** Access blocked by the base station for all mobile devices. */
    public static final int ACCESS_BLOCK_ALL = 0x829;
    /** Maximum access probes for the IS-707B call. */
    public static final int IS707B_MAX_ACC = 0x82A;
    /** Put device in thermal emergency. */
    public static final int THERMAL_EMERGENCY = 0x82B;                               /* no retry */
    /** In favor of a voice call or SMS when concurrent voice and data are not supported. */
    public static final int CCS_NOT_ALLOWED = 0x82C;
    /** The other clients rejected incoming call. */
    public static final int INCOM_REJ = 0x82D;
    /** No service on the gateway. */
    public static final int NO_GATEWAY_SRV = 0x82E;
    /** GPRS context is not available. */
    public static final int NO_GPRS_CONTEXT = 0x82F;
    /**
     * Network refuses service to the MS because either an identity of the MS is not acceptable to
     * the network or the MS does not pass the authentication check.
     */
    public static final int ILLEGAL_MS = 0x830;                                      /* no retry */
    /** ME could not be authenticated and the ME used is not acceptable to the network. */
    public static final int ILLEGAL_ME = 0x831;                                      /* no retry */
    /** Not allowed to operate either GPRS or non-GPRS services. */
    public static final int GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED = 0x832; /* no retry */
    /** MS is not allowed to operate GPRS services. */
    public static final int GPRS_SERVICES_NOT_ALLOWED = 0x833;                       /* no retry */
    /** No matching identity or context could be found in the network. */
    public static final int MS_IDENTITY_CANNOT_BE_DERIVED_BY_THE_NETWORK = 0x834;
    /**
     * Mobile reachable timer has expired, or the GMM context data related to the subscription dose
     * not exist in the SGSN.
     */
    public static final int IMPLICITLY_DETACHED = 0x835;
    /**
     * UE requests GPRS service, or the network initiates a detach request in a PLMN which does not
     * offer roaming for GPRS services to that MS.
     */
    public static final int PLMN_NOT_ALLOWED = 0x836;                                /* no retry */
    /**
     * MS requests service, or the network initiates a detach request, in a location area where the
     * HPLMN determines that the MS, by subscription, is not allowed to operate.
     */
    public static final int LA_NOT_ALLOWED = 0x837;                                  /* no retry */
    /**
     * UE requests GPRS service or the network initiates a detach request in a PLMN that does not
     * offer roaming for GPRS services.
     */
    public static final int GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN = 0x838;          /* no retry */
    /** PDP context already exists. */
    public static final int PDP_DUPLICATE = 0x839;
    /** RAT change on the UE. */
    public static final int UE_RAT_CHANGE = 0x83A;
    /** Network cannot serve a request from the MS due to congestion. */
    public static final int CONGESTION = 0x83B;
    /**
     * MS requests an establishment of the radio access bearers for all active PDP contexts by
     * sending a service request message indicating data to the network, but the SGSN does not have
     * any active PDP context.
     */
    public static final int NO_PDP_CONTEXT_ACTIVATED = 0x83C;
    /** Access class blocking restrictions for the current camped cell. */
    public static final int ACCESS_CLASS_DSAC_REJECTION = 0x83D;
    /** SM attempts PDP activation for a maximum of four attempts. */
    public static final int PDP_ACTIVATE_MAX_RETRY_FAILED = 0x83E;
    /** Radio access bearer failure. */
    public static final int RAB_FAILURE = 0x83F;
    /** Invalid EPS bearer identity in the request. */
    public static final int ESM_UNKNOWN_EPS_BEARER_CONTEXT = 0x840;
    /** Data radio bearer is released by the RRC. */
    public static final int DRB_RELEASED_AT_RRC = 0x841;
    /** Indicate the connection was released. */
    public static final int NAS_SIG_CONN_RELEASED = 0x842;
    /** UE is detached. */
    public static final int EMM_DETACHED = 0x843;
    /** Attach procedure is rejected by the network. */
    public static final int EMM_ATTACH_FAILED = 0x844;
    /** Attach procedure is started for EMC purposes. */
    public static final int EMM_ATTACH_STARTED = 0x845;
    /** Service request procedure failure. */
    public static final int LTE_NAS_SERVICE_REQ_FAILED = 0x846;
    /** Active dedication bearer was requested using the same default bearer ID. */
    public static final int ESM_ACTIVE_DEDICATED_BEARER_REACTIVATED_BY_NW = 0x847;
    /** Collision scenarios for the UE and network-initiated procedures. */
    public static final int ESM_LOWER_LAYER_FAILURE = 0x848;
    /** Bearer must be deactivated to synchronize with the network. */
    public static final int ESM_SYNC_UP_WITH_NW = 0x849;
    /** Active dedication bearer was requested for an existing default bearer. */
    public static final int ESM_NW_ACTIVATED_DED_BEARER_WITH_ID_OF_DEF_BEARER = 0x84A;
    /** Bad OTA message is received from the network. */
    public static final int ESM_BAD_OTA_MESSAGE = 0x84B;
    /** Download server rejected the call. */
    public static final int ESM_DS_REJECTED_THE_CALL = 0x84C;
    /** PDN was disconnected by the downlaod server due to IRAT. */
    public static final int ESM_CONTEXT_TRANSFERED_DUE_TO_IRAT = 0x84D;
    /** Dedicated bearer will be deactivated regardless of the network response. */
    public static final int DS_EXPLICIT_DEACT = 0x84E;
    /** No specific local cause is mentioned, usually a valid OTA cause. */
    public static final int ESM_LOCAL_CAUSE_NONE = 0x84F;
    /** Throttling is not needed for this service request failure. */
    public static final int LTE_NAS_SERVICE_REQ_FAILED_NO_THROTTLE = 0x850;
    /** Access control list check failure at the lower layer. */
    public static final int ACCESS_CONTROL_LIST_CHECK_FAILURE = 0x851;
    /** Service is not allowed on the requested PLMN. */
    public static final int LTE_NAS_SERVICE_REQ_FAILED_DS_DISALLOW = 0x852;
    /** T3417 timer expiration of the service request procedure. */
    public static final int EMM_T3417_EXPIRED = 0x853;
    /** Extended service request fails due to expiration of the T3417 EXT timer. */
    public static final int EMM_T3417_EXT_EXPIRED = 0x854;
    /** Transmission failure of uplink data. */
    public static final int LRRC_UL_DATA_CNF_FAILURE_TXN = 0x855;
    /** Uplink data delivery failed due to a handover. */
    public static final int LRRC_UL_DATA_CNF_FAILURE_HO = 0x856;
    /** Uplink data delivery failed due to a connection release. */
    public static final int LRRC_UL_DATA_CNF_FAILURE_CONN_REL = 0x857;
    /** Uplink data delivery failed due to a radio link failure. */
    public static final int LRRC_UL_DATA_CNF_FAILURE_RLF = 0x858;
    /** RRC is not connected but the NAS sends an uplink data request. */
    public static final int LRRC_UL_DATA_CNF_FAILURE_CTRL_NOT_CONN = 0x859;
    /** Connection failure at access stratum. */
    public static final int LRRC_CONN_EST_FAILURE = 0x85A;
    /** Connection establishment is aborted due to another procedure. */
    public static final int LRRC_CONN_EST_FAILURE_ABORTED = 0x85B;
    /** Connection establishment failed due to a lower layer RRC connection failure. */
    public static final int LRRC_CONN_EST_FAILURE_ACCESS_BARRED = 0x85C;
    /** Connection establishment failed due to cell reselection at access stratum. */
    public static final int LRRC_CONN_EST_FAILURE_CELL_RESEL = 0x85D;
    /** Connection establishment failed due to configuration failure at the RRC. */
    public static final int LRRC_CONN_EST_FAILURE_CONFIG_FAILURE = 0x85E;
    /** Connection could not be established in the time limit. */
    public static final int LRRC_CONN_EST_FAILURE_TIMER_EXPIRED = 0x85F;
    /** Connection establishment failed due to a link failure at the RRC. */
    public static final int LRRC_CONN_EST_FAILURE_LINK_FAILURE = 0x860;
    /** Connection establishment failed as the RRC is not camped on any cell. */
    public static final int LRRC_CONN_EST_FAILURE_NOT_CAMPED = 0x861;
    /** Connection establishment failed due to a service interval failure at the RRC. */
    public static final int LRRC_CONN_EST_FAILURE_SI_FAILURE = 0x862;
    /** Connection establishment failed due to the network rejecting the UE connection request. */
    public static final int LRRC_CONN_EST_FAILURE_CONN_REJECT = 0x863;
    /** Normal connection release. */
    public static final int LRRC_CONN_REL_NORMAL = 0x864;
    /** Connection release failed due to radio link failure conditions. */
    public static final int LRRC_CONN_REL_RLF = 0x865;
    /** Connection reestablishment failure. */
    public static final int LRRC_CONN_REL_CRE_FAILURE = 0x866;
    /** UE is out of service during the call register. */
    public static final int LRRC_CONN_REL_OOS_DURING_CRE = 0x867;
    /** Connection has been released by the RRC due to an abort request. */
    public static final int LRRC_CONN_REL_ABORTED = 0x868;
    /** Connection released due to a system information block read error. */
    public static final int LRRC_CONN_REL_SIB_READ_ERROR = 0x869;
    /** Network-initiated detach with reattach. */
    public static final int DETACH_WITH_REATTACH_LTE_NW_DETACH = 0x86A;
    /** Network-initiated detach without reattach. */
    public static final int DETACH_WITHOUT_REATTACH_LTE_NW_DETACH = 0x86B;
    /** ESM procedure maximum attempt timeout failure. */
    public static final int ESM_PROC_TIME_OUT = 0x86C;
    /**
     * No PDP exists with the given connection ID while modifying or deactivating or activation for
     * an already active PDP.
     */
    public static final int INVALID_CONNECTION_ID = 0x86D;
    /** Maximum NSAPIs have been exceeded during PDP activation. */
    public static final int INVALID_NSAPI = 0x86E;
    /** Primary context for NSAPI does not exist. */
    public static final int INVALID_PRI_NSAPI = 0x86F;
    /** Unable to encode the OTA message for MT PDP or deactivate PDP. */
    public static final int INVALID_FIELD = 0x870;
    /**
     * Radio access bearer is not established by the lower layers during activation, modification,
     * or deactivation.
     */
    public static final int RAB_SETUP_FAILURE = 0x871;
    /** Expiration of the PDP establish timer with a maximum of five retries. */
    public static final int PDP_ESTABLISH_MAX_TIMEOUT = 0x872;
    /** Expiration of the PDP modify timer with a maximum of four retries. */
    public static final int PDP_MODIFY_MAX_TIMEOUT = 0x873;
    /** Expiration of the PDP deactivate timer with a maximum of four retries. */
    public static final int PDP_INACTIVE_MAX_TIMEOUT = 0x874;
    /** PDP activation failed due to RRC_ABORT or a forbidden PLMN. */
    public static final int PDP_LOWERLAYER_ERROR = 0x875;
    /** MO PDP modify collision when the MT PDP is already in progress. */
    public static final int PDP_MODIFY_COLLISION = 0x876;
    /** Radio resource is not available. */
    public static final int SM_NO_RADIO_AVAILABLE = 0x877;
    /** Abort due to service not available. */
    public static final int SM_ABORT_SERVICE_NOT_AVAILABLE = 0x878;
    /** Maximum size of the L2 message was exceeded. */
    public static final int MESSAGE_EXCEED_MAX_L2_LIMIT = 0x879;
    /** NAS request was rejected by the network. */
    public static final int SM_NAS_SRV_REQ_FAILURE = 0x87A;
    /** RRC connection establishment failure due to an error in the request message. */
    public static final int RRC_CONN_EST_FAILURE_REQ_ERROR = 0x87B;
    /** RRC connection establishment failure due to a change in the tracking area ID. */
    public static final int RRC_CONN_EST_FAILURE_TAI_CHANGE = 0x87C;
    /** RRC connection establishment failure because the RF was unavailable. */
    public static final int RRC_CONN_EST_FAILURE_RF_UNAVAILABLE = 0x87D;
    /**
     * Connection was aborted before deactivating the LTE stack due to a successful LX IRAT.
     * (e.g., after IRAT handovers)
     */
    public static final int RRC_CONN_REL_ABORTED_IRAT_SUCCESS = 0x87E;
    /**
     * If the UE has an LTE radio link failure before security is established, the connection must
     * be released and the UE must return to idle.
     */
    public static final int RRC_CONN_REL_RLF_SEC_NOT_ACTIVE = 0x87F;
    /** Connection was aborted by the NAS after an IRAT to LTE IRAT handover. */
    public static final int RRC_CONN_REL_IRAT_TO_LTE_ABORTED = 0x880;
    /**
     * Connection was aborted before deactivating the LTE stack after a successful LR IRAT cell
     * change order procedure.
     */
    public static final int RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_SUCCESS = 0x881;
    /** Connection was aborted in the middle of a LG IRAT cell change order. */
    public static final int RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_ABORTED = 0x882;
    /** IMSI present in the UE is unknown in the home subscriber server. */
    public static final int IMSI_UNKNOWN_IN_HSS = 0x883;
    /** IMEI of the UE is not accepted by the network. */
    public static final int IMEI_NOT_ACCEPTED = 0x884;                               /* no retry */
    /** EPS and non-EPS services are not allowed by the network. */
    public static final int EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED = 0x885;   /* no retry */
    /** EPS services are not allowed in the PLMN. */
    public static final int EPS_SERVICES_NOT_ALLOWED_IN_PLMN = 0x886;                /* no retry */
    /** Mobile switching center is temporarily unreachable. */
    public static final int MSC_TEMPORARILY_NOT_REACHABLE = 0x887;
    /** CS domain is not available. */
    public static final int CS_DOMAIN_NOT_AVAILABLE = 0x888;
    /** ESM level failure. */
    public static final int ESM_FAILURE = 0x889;
    /** MAC level failure. */
    public static final int MAC_FAILURE = 0x88A;
    /** Synchronization failure. */
    public static final int SYNCH_FAILURE = 0x88B;
    /** UE security capabilities mismatch. */
    public static final int UE_SECURITY_CAPABILITIES_MISMATCH = 0x88C;
    /** Unspecified security mode reject. */
    public static final int SECURITY_MODE_REJ_UNSPECIFIED = 0x88D;
    /** Unacceptable non-EPS authentication. */
    public static final int NON_EPS_AUTH_UNACCEPTABLE = 0x88E;
    /** CS fallback call establishment is not allowed. */
    public static final int CS_FALLBACK_CALL_EST_NOT_ALLOWED = 0x88F;
    /** No EPS bearer context was activated. */
    public static final int NO_EPS_BEARER_CONTEXT_ACTIVATED = 0x890;
    /** Invalid EMM state. */
    public static final int EMM_INVALID_STATE = 0x891;
    /** Non-Access Spectrum layer failure. */
    public static final int NAS_LAYER_FAILURE = 0x892;
    /** Multiple PDP call feature is disabled. */
    public static final int MULTI_PDN_NOT_ALLOWED = 0x893;
    /** Data call has been brought down because EMBMS is not enabled at the RRC layer. */
    public static final int EMBMS_NOT_ENABLED = 0x894;
    /** Data call was unsuccessfully transferred during the IRAT handover. */
    public static final int PENDING_REDIAL_CALL_CLEANUP = 0x895;
    /** EMBMS data call has been successfully brought down. */
    public static final int EMBMS_REGULAR_DEACTIVATION = 0x896;
    /** Test loop-back data call has been successfully brought down. */
    public static final int TLB_REGULAR_DEACTIVATION = 0x897;
    /** Lower layer registration failure. */
    public static final int LOWER_LAYER_REGISTRATION_FAILURE = 0x898;
    /**
     * Network initiates a detach on LTE with error cause ""data plan has been replenished or has
     * expired.
     */
    public static final int DETACH_EPS_SERVICES_NOT_ALLOWED = 0x899;
    /** UMTS interface is brought down due to handover from UMTS to iWLAN. */
    public static final int SM_INTERNAL_PDP_DEACTIVATION = 0x89A;
    /** The reception of a connection deny message with a deny code of general or network busy. */
    public static final int CD_GEN_OR_BUSY = 0x89B;
    /**
     * The reception of a connection deny message with a deny code of billing failure or
     * authentication failure.
     */
    public static final int CD_BILL_OR_AUTH = 0x89C;                                 /* no retry */
    /** HDR system has been changed due to redirection or the PRL was not preferred. */
    public static final int HDR_CHANGED = 0x89D;
    /** Device exited HDR due to redirection or the PRL was not preferred. */
    public static final int HDR_EXITED = 0x89E;
    /** Device does not have an HDR session. */
    public static final int HDR_NO_SESSION = 0x89F;
    /** It is ending an HDR call origination in favor of a GPS fix. */
    public static final int HDR_ORIG_DURING_GPS_FIX = 0x8A0;
    /** Connection setup on the HDR system was time out. */
    public static final int HDR_CS_TIMEOUT = 0x8A1;
    /** Device failed to acquire a co-located HDR for origination. */
    public static final int COLLOC_ACQ_FAIL = 0x8A2;
    /** OTASP commit is in progress. */
    public static final int OTASP_COMMIT_IN_PROG = 0x8A3;
    /** Device has no hybrid HDR service. */
    public static final int NO_HYBR_HDR_SRV = 0x8A4;
    /** HDR module could not be obtained because of the RF locked. */
    public static final int HDR_NO_LOCK_GRANTED = 0x8A5;
    /** DBM or SMS is in progress. */
    public static final int HOLD_OTHER_IN_PROG = 0x8A6;
    /** HDR module released the call due to fade. */
    public static final int HDR_FADE = 0x8A7;
    /** HDR system access failure. */
    public static final int HDR_ACC_FAIL = 0x8A8;
    /**
     * P_rev supported by 1 base station is less than 6, which is not supported for a 1X data call.
     * The UE must be in the footprint of BS which has p_rev >= 6 to support this SO33 call.
     */
    public static final int UNSUPPORTED_1X_PREV = 0x8A9;
    /** Client ended the data call. */
    public static final int LOCAL_END = 0x8AA;
    /** Device has no service. */
    public static final int NO_SRV = 0x8AB;
    /** Device lost the system due to fade. */
    public static final int FADE = 0x8AC;
    /** Receiving a release from the base station with no reason. */
    public static final int REL_NORMAL = 0x8AD;
    /** Access attempt is already in progress. */
    public static final int ACC_IN_PROG = 0x8AE;
    /** Access failure. */
    public static final int ACC_FAIL = 0x8AF;
    /** Device is in the process of redirecting or handing off to a different target system. */
    public static final int REDIR_OR_HANDOFF = 0x8B0;
    /** Device is operating in Emergency mode. */
    public static final int EMERGENCY_MODE = 0x8B1;                                  /* no retry */
    /** Device is in use (e.g., voice call). */
    public static final int PHONE_IN_USE = 0x8B2;
    /**
     * Device operational mode is different from the mode requested in the traffic channel bring up
     */
    public static final int INVALID_MODE = 0x8B3;                                    /* no retry */
    /** SIM was marked by the network as invalid for the circuit and/or packet service domain. */
    public static final int INVALID_SIM_STATE = 0x8B4;                               /* no retry */
    /** There is no co-located HDR. */
    public static final int NO_COLLOC_HDR = 0x8B5;
    /** UE is entering power save mode. */
    public static final int EMM_DETACHED_PSM = 0x8B6;                                /* no retry */
    /** Dual switch from single standby to dual standby is in progress. */
    public static final int DUAL_SWITCH = 0x8B7;
    /**
     * Data call bring up fails in the PPP setup due to a timeout.
     * (e.g., an LCP conf ack was not received from the network)
     */
    public static final int PPP_TIMEOUT = 0x8B8;
    /**
     * Data call bring up fails in the PPP setup due to an authorization failure.
     * (e.g., authorization is required, but not negotiated with the network during an LCP phase)
     */
    public static final int PPP_AUTH_FAILURE = 0x8B9;                                /* no retry */
    /** Data call bring up fails in the PPP setup due to an option mismatch. */
    public static final int PPP_OPTION_MISMATCH = 0x8BA;                             /* no retry */
    /** Data call bring up fails in the PPP setup due to a PAP failure. */
    public static final int PPP_PAP_FAILURE = 0x8BB;                                 /* no retry */
    /** Data call bring up fails in the PPP setup due to a CHAP failure. */
    public static final int PPP_CHAP_FAILURE = 0x8BC;                                /* no retry */
    /**
     * Data call bring up fails in the PPP setup because the PPP is in the process of cleaning the
     * previous PPP session.
     */
    public static final int PPP_ERR_CLOSE_IN_PROGRESS = 0x8BD;
    /**
     * IPv6 interface bring up fails because the network provided only the IPv4 address for the
     * upcoming PDN permanent client can reattempt a IPv6 call bring up after the IPv4 interface is
     * also brought down. However, there is no guarantee that the network will provide a IPv6
     * address.
     */
    public static final int EHRPD_SUBS_LIMITED_TO_V4 = 0x8BE;                        /* no retry */
    /**
     * IPv4 interface bring up fails because the network provided only the IPv6 address for the
     * upcoming PDN permanent client can reattempt a IPv4 call bring up after the IPv6 interface is
     * also brought down. However there is no guarantee that the network will provide a IPv4
     * address.
     */
    public static final int EHRPD_SUBS_LIMITED_TO_V6 = 0x8BF;                        /* no retry */
    /** Data call bring up fails in the VSNCP phase due to a VSNCP timeout error. */
    public static final int EHRPD_VSNCP_TIMEOUT = 0x8C0;
    /** Data call bring up fails in the VSNCP phase due to a general error. */
    public static final int EHRPD_VSNCP_GEN_ERROR = 0x8C1;                           /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request because the requested APN is unauthorized.
     */
    public static final int EHRPD_VSNCP_UNAUTH_APN = 0x8C2;                          /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request because the PDN limit has been exceeded.
     */
    public static final int EHRPD_VSNCP_PDN_LIMIT_EXCEED = 0x8C3;                    /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase because the network rejected the VSNCP
     * configuration request due to no PDN gateway.
     */
    public static final int EHRPD_VSNCP_NO_PDN_GW = 0x8C4;                           /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request because the PDN gateway is unreachable.
     */
    public static final int EHRPD_VSNCP_PDN_GW_UNREACH = 0x8C5;                      /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request due to a PDN gateway reject.
     */
    public static final int EHRPD_VSNCP_PDN_GW_REJ = 0x8C6;                          /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request with the reason of insufficient parameter.
     */
    public static final int EHRPD_VSNCP_INSUFF_PARAM = 0x8C7;                        /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request with the reason of resource unavailable.
     */
    public static final int EHRPD_VSNCP_RESOURCE_UNAVAIL = 0x8C8;                    /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request with the reason of admin prohibited.
     */
    public static final int EHRPD_VSNCP_ADMIN_PROHIBIT = 0x8C9;                      /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of PDN ID in use, or
     * all existing PDNs are brought down with this end reason because one of the PDN bring up was
     * rejected by the network with the reason of PDN ID in use.
     */
    public static final int EHRPD_VSNCP_PDN_ID_IN_USE = 0x8CA;
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request for the reason of subscriber limitation.
     */
    public static final int EHRPD_VSNCP_SUBSCR_LIMITATION = 0x8CB;                   /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request because the PDN exists for this APN.
     */
    public static final int EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN = 0x8CC;             /* no retry */
    /**
     * Data call bring up fails in the VSNCP phase due to a network rejection of the VSNCP
     * configuration request with reconnect to this PDN not allowed, or an active data call is
     * terminated by the network because reconnection to this PDN is not allowed. Upon receiving
     * this error code from the network, the modem infinitely throttles the PDN until the next
     * power cycle.
     */
    public static final int EHRPD_VSNCP_RECONNECT_NOT_ALLOWED = 0x8CD;               /* no retry */
    /** Device failure to obtain the prefix from the network. */
    public static final int IPV6_PREFIX_UNAVAILABLE = 0x8CE;
    /** System preference change back to SRAT during handoff */
    public static final int HANDOFF_PREF_SYS_BACK_TO_SRAT = 0x8CF;

    // OEM sepecific error codes. To be used by OEMs when they don't
    // want to reveal error code which would be replaced by ERROR_UNSPECIFIED
    public static final int OEM_DCFAILCAUSE_1 = 0x1001;
    public static final int OEM_DCFAILCAUSE_2 = 0x1002;
    public static final int OEM_DCFAILCAUSE_3 = 0x1003;
    public static final int OEM_DCFAILCAUSE_4 = 0x1004;
    public static final int OEM_DCFAILCAUSE_5 = 0x1005;
    public static final int OEM_DCFAILCAUSE_6 = 0x1006;
    public static final int OEM_DCFAILCAUSE_7 = 0x1007;
    public static final int OEM_DCFAILCAUSE_8 = 0x1008;
    public static final int OEM_DCFAILCAUSE_9 = 0x1009;
    public static final int OEM_DCFAILCAUSE_10 = 0x100A;
    public static final int OEM_DCFAILCAUSE_11 = 0x100B;
    public static final int OEM_DCFAILCAUSE_12 = 0x100C;
    public static final int OEM_DCFAILCAUSE_13 = 0x100D;
    public static final int OEM_DCFAILCAUSE_14 = 0x100E;
    public static final int OEM_DCFAILCAUSE_15 = 0x100F;

    // Local errors generated by Vendor RIL
    // specified in ril.h
    public static final int REGISTRATION_FAIL = -1;
    public static final int GPRS_REGISTRATION_FAIL = -2;
    public static final int SIGNAL_LOST = -3;                                        /* no retry */
    public static final int PREF_RADIO_TECH_CHANGED = -4;
    public static final int RADIO_POWER_OFF = -5;                                    /* no retry */
    public static final int TETHERED_CALL_ACTIVE = -6;                               /* no retry */
    public static final int ERROR_UNSPECIFIED = 0xFFFF;

    // Errors generated by the Framework
    // specified here
    public static final int UNKNOWN = 0x10000;
    public static final int RADIO_NOT_AVAILABLE = 0x10001;                           /* no retry */
    public static final int UNACCEPTABLE_NETWORK_PARAMETER = 0x10002;                /* no retry */
    public static final int CONNECTION_TO_DATACONNECTIONAC_BROKEN = 0x10003;
    public static final int LOST_CONNECTION = 0x10004;
    /** Data was reset by framework. */
    public static final int RESET_BY_FRAMEWORK = 0x10005;

    /** @hide */
    @IntDef(value = {
            NONE,
            OPERATOR_BARRED,
            NAS_SIGNALLING,
            LLC_SNDCP,
            INSUFFICIENT_RESOURCES,
            MISSING_UNKNOWN_APN,
            UNKNOWN_PDP_ADDRESS_TYPE,
            USER_AUTHENTICATION,
            ACTIVATION_REJECT_GGSN,
            ACTIVATION_REJECT_UNSPECIFIED,
            SERVICE_OPTION_NOT_SUPPORTED,
            SERVICE_OPTION_NOT_SUBSCRIBED,
            SERVICE_OPTION_OUT_OF_ORDER,
            NSAPI_IN_USE,
            REGULAR_DEACTIVATION,
            QOS_NOT_ACCEPTED,
            NETWORK_FAILURE,
            UMTS_REACTIVATION_REQ,
            FEATURE_NOT_SUPP,
            TFT_SEMANTIC_ERROR,
            TFT_SYTAX_ERROR,
            UNKNOWN_PDP_CONTEXT,
            FILTER_SEMANTIC_ERROR,
            FILTER_SYTAX_ERROR,
            PDP_WITHOUT_ACTIVE_TFT,
            ACTIVATION_REJECTED_BCM_VIOLATION,
            ONLY_IPV4_ALLOWED,
            ONLY_IPV6_ALLOWED,
            ONLY_SINGLE_BEARER_ALLOWED,
            ESM_INFO_NOT_RECEIVED,
            PDN_CONN_DOES_NOT_EXIST,
            MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED,
            COLLISION_WITH_NW_INIT_REQ,
            ONLY_IPV4V6_ALLOWED,
            ONLY_NON_IP_ALLOWED,
            UNSUPPORTED_QCI_VALUE,
            BEARER_HANDLING_NOT_SUPPORTED,
            MAX_ACTIVE_PDP_CONTEXT_REACHED,
            UNSUPPORTED_APN_IN_CURRENT_PLMN,
            INVALID_TRANSACTION_ID,
            MESSAGE_INCORRECT_SEMANTIC,
            INVALID_MANDATORY_INFO,
            MESSAGE_TYPE_UNSUPPORTED,
            MSG_TYPE_NONCOMPATIBLE_STATE,
            UNKNOWN_INFO_ELEMENT,
            CONDITIONAL_IE_ERROR,
            MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE,
            PROTOCOL_ERRORS,                 /* no retry */
            APN_TYPE_CONFLICT,
            INVALID_PCSCF_ADDR,
            INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN,
            EMM_ACCESS_BARRED,
            EMERGENCY_IFACE_ONLY,
            IFACE_MISMATCH,
            COMPANION_IFACE_IN_USE,
            IP_ADDRESS_MISMATCH,
            IFACE_AND_POL_FAMILY_MISMATCH,
            EMM_ACCESS_BARRED_INFINITE_RETRY,
            AUTH_FAILURE_ON_EMERGENCY_CALL,
            INVALID_DNS_ADDR,
            INVALID_PCSCF_DNS_ADDR,
            CALL_PREEMPT_BY_EMERGENCY_APN,
            UE_INIT_DETACH_OR_DISCONNECT,
            MIP_FA_REASON_UNSPECIFIED,
            MIP_FA_ADMIN_PROHIBITED,
            MIP_FA_INSUFFICIENT_RESOURCES,
            MIP_FA_MOBILE_NODE_AUTH_FAILURE,
            MIP_FA_HA_AUTH_FAILURE,
            MIP_FA_REQ_LIFETIME_TOO_LONG,
            MIP_FA_MALFORMED_REQUEST,
            MIP_FA_MALFORMED_REPLY,
            MIP_FA_ENCAPSULATION_UNAVAILABLE,
            MIP_FA_VJHC_UNAVAILABLE,
            MIP_FA_REV_TUNNEL_UNAVAILABLE,
            MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET,
            MIP_FA_DELIVERY_STYLE_NOT_SUPP,
            MIP_FA_MISSING_NAI,
            MIP_FA_MISSING_HA,
            MIP_FA_MISSING_HOME_ADDR,
            MIP_FA_UNKNOWN_CHALLENGE,
            MIP_FA_MISSING_CHALLENGE,
            MIP_FA_STALE_CHALLENGE,
            MIP_HA_REASON_UNSPECIFIED,
            MIP_HA_ADMIN_PROHIBITED,
            MIP_HA_INSUFFICIENT_RESOURCES,
            MIP_HA_MOBILE_NODE_AUTH_FAILURE,
            MIP_HA_FA_AUTH_FAILURE,
            MIP_HA_REGISTRATION_ID_MISMATCH,
            MIP_HA_MALFORMED_REQUEST,
            MIP_HA_UNKNOWN_HA_ADDR,
            MIP_HA_REV_TUNNEL_UNAVAILABLE,
            MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET,
            MIP_HA_ENCAPSULATION_UNAVAILABLE,
            CLOSE_IN_PROGRESS,
            NW_INITIATED_TERMINATION,
            MODEM_APP_PREEMPTED,
            ERR_PDN_IPV4_CALL_DISALLOWED,
            ERR_PDN_IPV4_CALL_THROTTLED,
            ERR_PDN_IPV6_CALL_DISALLOWED,
            ERR_PDN_IPV6_CALL_THROTTLED,
            MODEM_RESTART,
            PDP_PPP_NOT_SUPPORTED,
            UNPREFERRED_RAT,
            PHYS_LINK_CLOSE_IN_PROGRESS,
            APN_PENDING_HANDOVER,
            PROFILE_BEARER_INCOMPATIBLE,
            SIM_CARD_EVT,
            LPM_OR_PWR_DOWN,
            APN_DISABLED,
            MAX_PPP_INACTIVITY_TIMER_EXPIRED,
            IPV6_ADDR_TRANSFER_FAILED,
            TRAT_SWAP_FAILED,
            EHRPD_TO_HRPD_FALLBACK,
            MIP_CONFIG_FAILURE,
            PDN_INACTIVITY_TIMER_EXPIRED,
            MAX_V4_CONNECTIONS,
            MAX_V6_CONNECTIONS,
            APN_MISMATCH,
            IP_VERSION_MISMATCH,
            DUN_CALL_DISALLOWED,
            INTERNAL_EPC_NONEPC_TRANSITION,
            IFACE_IN_USE,
            APN_DISALLOWED_ON_ROAMING,
            APN_PARAM_CHANGED,
            NULL_APN_DISALLOWED,
            THERMAL_MITIGATION,
            DATA_SETTINGS_DISABLED,
            DATA_ROAMING_SETTINGS_DISABLED,
            DDS_CALL_ABORT,
            INVALID_APN_NAME,
            DDS_SWITCH_IN_PROGRESS,
            CALL_DISALLOWED_IN_ROAMING,
            NON_IP_NOT_SUPPORTED,
            ERR_PDN_NON_IP_CALL_THROTTLED,
            ERR_PDN_NON_IP_CALL_DISALLOWED,
            CDMA_LOCK,
            CDMA_INTERCEPT,
            CDMA_REORDER,
            CDMA_REL_SO_REJ,
            CDMA_INCOM_CALL,
            CDMA_ALERT_STOP,
            CHANNEL_ACQUISITION_FAILURE,
            MAX_ACCESS_PROBE,
            CCS_NOT_SUPPORTED_BY_BS,
            NO_RESPONSE_FROM_BS,
            REJECTED_BY_BS,
            CCS_INCOMPATIBLE,
            NO_CDMA_SRV,
            UIM_NOT_PRESENT,
            CDMA_RETRY_ORDER,
            ACCESS_BLOCK,
            ACCESS_BLOCK_ALL,
            IS707B_MAX_ACC,
            THERMAL_EMERGENCY,
            CCS_NOT_ALLOWED,
            INCOM_REJ,
            NO_GATEWAY_SRV,
            NO_GPRS_CONTEXT,
            ILLEGAL_MS,
            ILLEGAL_ME,
            GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED,
            GPRS_SERVICES_NOT_ALLOWED,
            MS_IDENTITY_CANNOT_BE_DERIVED_BY_THE_NETWORK,
            IMPLICITLY_DETACHED,
            PLMN_NOT_ALLOWED,
            LA_NOT_ALLOWED,
            GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN,
            PDP_DUPLICATE,
            UE_RAT_CHANGE,
            CONGESTION,
            NO_PDP_CONTEXT_ACTIVATED,
            ACCESS_CLASS_DSAC_REJECTION,
            PDP_ACTIVATE_MAX_RETRY_FAILED,
            RAB_FAILURE,
            ESM_UNKNOWN_EPS_BEARER_CONTEXT,
            DRB_RELEASED_AT_RRC,
            NAS_SIG_CONN_RELEASED,
            EMM_DETACHED,
            EMM_ATTACH_FAILED,
            EMM_ATTACH_STARTED,
            LTE_NAS_SERVICE_REQ_FAILED,
            ESM_ACTIVE_DEDICATED_BEARER_REACTIVATED_BY_NW,
            ESM_LOWER_LAYER_FAILURE,
            ESM_SYNC_UP_WITH_NW,
            ESM_NW_ACTIVATED_DED_BEARER_WITH_ID_OF_DEF_BEARER,
            ESM_BAD_OTA_MESSAGE,
            ESM_DS_REJECTED_THE_CALL,
            ESM_CONTEXT_TRANSFERED_DUE_TO_IRAT,
            DS_EXPLICIT_DEACT,
            ESM_LOCAL_CAUSE_NONE,
            LTE_NAS_SERVICE_REQ_FAILED_NO_THROTTLE,
            ACCESS_CONTROL_LIST_CHECK_FAILURE,
            LTE_NAS_SERVICE_REQ_FAILED_DS_DISALLOW,
            EMM_T3417_EXPIRED,
            EMM_T3417_EXT_EXPIRED,
            LRRC_UL_DATA_CNF_FAILURE_TXN,
            LRRC_UL_DATA_CNF_FAILURE_HO,
            LRRC_UL_DATA_CNF_FAILURE_CONN_REL,
            LRRC_UL_DATA_CNF_FAILURE_RLF,
            LRRC_UL_DATA_CNF_FAILURE_CTRL_NOT_CONN,
            LRRC_CONN_EST_FAILURE,
            LRRC_CONN_EST_FAILURE_ABORTED,
            LRRC_CONN_EST_FAILURE_ACCESS_BARRED,
            LRRC_CONN_EST_FAILURE_CELL_RESEL,
            LRRC_CONN_EST_FAILURE_CONFIG_FAILURE,
            LRRC_CONN_EST_FAILURE_TIMER_EXPIRED,
            LRRC_CONN_EST_FAILURE_LINK_FAILURE,
            LRRC_CONN_EST_FAILURE_NOT_CAMPED,
            LRRC_CONN_EST_FAILURE_SI_FAILURE,
            LRRC_CONN_EST_FAILURE_CONN_REJECT,
            LRRC_CONN_REL_NORMAL,
            LRRC_CONN_REL_RLF,
            LRRC_CONN_REL_CRE_FAILURE,
            LRRC_CONN_REL_OOS_DURING_CRE,
            LRRC_CONN_REL_ABORTED,
            LRRC_CONN_REL_SIB_READ_ERROR,
            DETACH_WITH_REATTACH_LTE_NW_DETACH,
            DETACH_WITHOUT_REATTACH_LTE_NW_DETACH,
            ESM_PROC_TIME_OUT,
            INVALID_CONNECTION_ID,
            INVALID_NSAPI,
            INVALID_PRI_NSAPI,
            INVALID_FIELD,
            RAB_SETUP_FAILURE,
            PDP_ESTABLISH_MAX_TIMEOUT,
            PDP_MODIFY_MAX_TIMEOUT,
            PDP_INACTIVE_MAX_TIMEOUT,
            PDP_LOWERLAYER_ERROR,
            PDP_MODIFY_COLLISION,
            SM_NO_RADIO_AVAILABLE,
            SM_ABORT_SERVICE_NOT_AVAILABLE,
            MESSAGE_EXCEED_MAX_L2_LIMIT,
            SM_NAS_SRV_REQ_FAILURE,
            RRC_CONN_EST_FAILURE_REQ_ERROR,
            RRC_CONN_EST_FAILURE_TAI_CHANGE,
            RRC_CONN_EST_FAILURE_RF_UNAVAILABLE,
            RRC_CONN_REL_ABORTED_IRAT_SUCCESS,
            RRC_CONN_REL_RLF_SEC_NOT_ACTIVE,
            RRC_CONN_REL_IRAT_TO_LTE_ABORTED,
            RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_SUCCESS,
            RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_ABORTED,
            IMSI_UNKNOWN_IN_HSS,
            IMEI_NOT_ACCEPTED,
            EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED,
            EPS_SERVICES_NOT_ALLOWED_IN_PLMN,
            MSC_TEMPORARILY_NOT_REACHABLE,
            CS_DOMAIN_NOT_AVAILABLE,
            ESM_FAILURE,
            MAC_FAILURE,
            SYNCH_FAILURE,
            UE_SECURITY_CAPABILITIES_MISMATCH,
            SECURITY_MODE_REJ_UNSPECIFIED,
            NON_EPS_AUTH_UNACCEPTABLE,
            CS_FALLBACK_CALL_EST_NOT_ALLOWED,
            NO_EPS_BEARER_CONTEXT_ACTIVATED,
            EMM_INVALID_STATE,
            NAS_LAYER_FAILURE,
            MULTI_PDN_NOT_ALLOWED,
            EMBMS_NOT_ENABLED,
            PENDING_REDIAL_CALL_CLEANUP,
            EMBMS_REGULAR_DEACTIVATION,
            TLB_REGULAR_DEACTIVATION,
            LOWER_LAYER_REGISTRATION_FAILURE,
            DETACH_EPS_SERVICES_NOT_ALLOWED,
            SM_INTERNAL_PDP_DEACTIVATION,
            CD_GEN_OR_BUSY,
            CD_BILL_OR_AUTH,
            HDR_CHANGED,
            HDR_EXITED,
            HDR_NO_SESSION,
            HDR_ORIG_DURING_GPS_FIX,
            HDR_CS_TIMEOUT,
            COLLOC_ACQ_FAIL,
            OTASP_COMMIT_IN_PROG,
            NO_HYBR_HDR_SRV,
            HDR_NO_LOCK_GRANTED,
            HOLD_OTHER_IN_PROG,
            HDR_FADE,
            HDR_ACC_FAIL,
            UNSUPPORTED_1X_PREV,
            LOCAL_END,
            NO_SRV,
            FADE,
            REL_NORMAL,
            ACC_IN_PROG,
            ACC_FAIL,
            REDIR_OR_HANDOFF,
            EMERGENCY_MODE,
            PHONE_IN_USE,
            INVALID_MODE,
            INVALID_SIM_STATE,
            NO_COLLOC_HDR,
            EMM_DETACHED_PSM,
            DUAL_SWITCH,
            PPP_TIMEOUT,
            PPP_AUTH_FAILURE,
            PPP_OPTION_MISMATCH,
            PPP_PAP_FAILURE,
            PPP_CHAP_FAILURE,
            PPP_ERR_CLOSE_IN_PROGRESS,
            EHRPD_SUBS_LIMITED_TO_V4,
            EHRPD_SUBS_LIMITED_TO_V6,
            EHRPD_VSNCP_TIMEOUT,
            EHRPD_VSNCP_GEN_ERROR,
            EHRPD_VSNCP_UNAUTH_APN,
            EHRPD_VSNCP_PDN_LIMIT_EXCEED,
            EHRPD_VSNCP_NO_PDN_GW,
            EHRPD_VSNCP_PDN_GW_UNREACH,
            EHRPD_VSNCP_PDN_GW_REJ,
            EHRPD_VSNCP_INSUFF_PARAM,
            EHRPD_VSNCP_RESOURCE_UNAVAIL,
            EHRPD_VSNCP_ADMIN_PROHIBIT,
            EHRPD_VSNCP_PDN_ID_IN_USE,
            EHRPD_VSNCP_SUBSCR_LIMITATION,
            EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN,
            EHRPD_VSNCP_RECONNECT_NOT_ALLOWED,
            IPV6_PREFIX_UNAVAILABLE,
            HANDOFF_PREF_SYS_BACK_TO_SRAT,
            OEM_DCFAILCAUSE_1,
            OEM_DCFAILCAUSE_2,
            OEM_DCFAILCAUSE_3,
            OEM_DCFAILCAUSE_4,
            OEM_DCFAILCAUSE_5,
            OEM_DCFAILCAUSE_6,
            OEM_DCFAILCAUSE_7,
            OEM_DCFAILCAUSE_8,
            OEM_DCFAILCAUSE_9,
            OEM_DCFAILCAUSE_10,
            OEM_DCFAILCAUSE_11,
            OEM_DCFAILCAUSE_12,
            OEM_DCFAILCAUSE_13,
            OEM_DCFAILCAUSE_14,
            OEM_DCFAILCAUSE_15,
            REGISTRATION_FAIL,
            GPRS_REGISTRATION_FAIL,
            SIGNAL_LOST,
            PREF_RADIO_TECH_CHANGED,
            RADIO_POWER_OFF,
            TETHERED_CALL_ACTIVE,
            ERROR_UNSPECIFIED,
            UNKNOWN,
            RADIO_NOT_AVAILABLE,
            UNACCEPTABLE_NETWORK_PARAMETER,
            CONNECTION_TO_DATACONNECTIONAC_BROKEN,
            LOST_CONNECTION,
            RESET_BY_FRAMEWORK
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FailCause{}

    private static final Map<Integer, String> sFailCauseMap;
    static {
        sFailCauseMap = new HashMap<>();
        sFailCauseMap.put(NONE, "NONE");
        sFailCauseMap.put(OPERATOR_BARRED, "OPERATOR_BARRED");
        sFailCauseMap.put(NAS_SIGNALLING, "NAS_SIGNALLING");
        sFailCauseMap.put(LLC_SNDCP, "LLC_SNDCP");
        sFailCauseMap.put(INSUFFICIENT_RESOURCES, "INSUFFICIENT_RESOURCES");
        sFailCauseMap.put(MISSING_UNKNOWN_APN, "MISSING_UNKNOWN_APN");
        sFailCauseMap.put(UNKNOWN_PDP_ADDRESS_TYPE, "UNKNOWN_PDP_ADDRESS_TYPE");
        sFailCauseMap.put(USER_AUTHENTICATION, "USER_AUTHENTICATION");
        sFailCauseMap.put(ACTIVATION_REJECT_GGSN, "ACTIVATION_REJECT_GGSN");
        sFailCauseMap.put(ACTIVATION_REJECT_UNSPECIFIED,
                "ACTIVATION_REJECT_UNSPECIFIED");
        sFailCauseMap.put(SERVICE_OPTION_NOT_SUPPORTED,
                "SERVICE_OPTION_NOT_SUPPORTED");
        sFailCauseMap.put(SERVICE_OPTION_NOT_SUBSCRIBED,
                "SERVICE_OPTION_NOT_SUBSCRIBED");
        sFailCauseMap.put(SERVICE_OPTION_OUT_OF_ORDER, "SERVICE_OPTION_OUT_OF_ORDER");
        sFailCauseMap.put(NSAPI_IN_USE, "NSAPI_IN_USE");
        sFailCauseMap.put(REGULAR_DEACTIVATION, "REGULAR_DEACTIVATION");
        sFailCauseMap.put(QOS_NOT_ACCEPTED, "QOS_NOT_ACCEPTED");
        sFailCauseMap.put(NETWORK_FAILURE, "NETWORK_FAILURE");
        sFailCauseMap.put(UMTS_REACTIVATION_REQ, "UMTS_REACTIVATION_REQ");
        sFailCauseMap.put(FEATURE_NOT_SUPP, "FEATURE_NOT_SUPP");
        sFailCauseMap.put(TFT_SEMANTIC_ERROR, "TFT_SEMANTIC_ERROR");
        sFailCauseMap.put(TFT_SYTAX_ERROR, "TFT_SYTAX_ERROR");
        sFailCauseMap.put(UNKNOWN_PDP_CONTEXT, "UNKNOWN_PDP_CONTEXT");
        sFailCauseMap.put(FILTER_SEMANTIC_ERROR, "FILTER_SEMANTIC_ERROR");
        sFailCauseMap.put(FILTER_SYTAX_ERROR, "FILTER_SYTAX_ERROR");
        sFailCauseMap.put(PDP_WITHOUT_ACTIVE_TFT, "PDP_WITHOUT_ACTIVE_TFT");
        sFailCauseMap.put(ACTIVATION_REJECTED_BCM_VIOLATION, "ACTIVATION_REJECTED_BCM_VIOLATION");
        sFailCauseMap.put(ONLY_IPV4_ALLOWED, "ONLY_IPV4_ALLOWED");
        sFailCauseMap.put(ONLY_IPV6_ALLOWED, "ONLY_IPV6_ALLOWED");
        sFailCauseMap.put(ONLY_SINGLE_BEARER_ALLOWED, "ONLY_SINGLE_BEARER_ALLOWED");
        sFailCauseMap.put(ESM_INFO_NOT_RECEIVED, "ESM_INFO_NOT_RECEIVED");
        sFailCauseMap.put(PDN_CONN_DOES_NOT_EXIST, "PDN_CONN_DOES_NOT_EXIST");
        sFailCauseMap.put(MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED,
                "MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED");
        sFailCauseMap.put(COLLISION_WITH_NW_INIT_REQ, "COLLISION_WITH_NW_INIT_REQ");
        sFailCauseMap.put(ONLY_IPV4V6_ALLOWED, "ONLY_IPV4V6_ALLOWED");
        sFailCauseMap.put(ONLY_NON_IP_ALLOWED, "ONLY_NON_IP_ALLOWED");
        sFailCauseMap.put(UNSUPPORTED_QCI_VALUE, "UNSUPPORTED_QCI_VALUE");
        sFailCauseMap.put(BEARER_HANDLING_NOT_SUPPORTED, "BEARER_HANDLING_NOT_SUPPORTED");
        sFailCauseMap.put(MAX_ACTIVE_PDP_CONTEXT_REACHED,
                "MAX_ACTIVE_PDP_CONTEXT_REACHED");
        sFailCauseMap.put(UNSUPPORTED_APN_IN_CURRENT_PLMN,
                "UNSUPPORTED_APN_IN_CURRENT_PLMN");
        sFailCauseMap.put(INVALID_TRANSACTION_ID, "INVALID_TRANSACTION_ID");
        sFailCauseMap.put(MESSAGE_INCORRECT_SEMANTIC, "MESSAGE_INCORRECT_SEMANTIC");
        sFailCauseMap.put(INVALID_MANDATORY_INFO, "INVALID_MANDATORY_INFO");
        sFailCauseMap.put(MESSAGE_TYPE_UNSUPPORTED, "MESSAGE_TYPE_UNSUPPORTED");
        sFailCauseMap.put(MSG_TYPE_NONCOMPATIBLE_STATE, "MSG_TYPE_NONCOMPATIBLE_STATE");
        sFailCauseMap.put(UNKNOWN_INFO_ELEMENT, "UNKNOWN_INFO_ELEMENT");
        sFailCauseMap.put(CONDITIONAL_IE_ERROR, "CONDITIONAL_IE_ERROR");
        sFailCauseMap.put(MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE,
                "MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE");
        sFailCauseMap.put(PROTOCOL_ERRORS, "PROTOCOL_ERRORS");
        sFailCauseMap.put(APN_TYPE_CONFLICT, "APN_TYPE_CONFLICT");
        sFailCauseMap.put(INVALID_PCSCF_ADDR, "INVALID_PCSCF_ADDR");
        sFailCauseMap.put(INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN,
                "INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN");
        sFailCauseMap.put(EMM_ACCESS_BARRED, "EMM_ACCESS_BARRED");
        sFailCauseMap.put(EMERGENCY_IFACE_ONLY, "EMERGENCY_IFACE_ONLY");
        sFailCauseMap.put(IFACE_MISMATCH, "IFACE_MISMATCH");
        sFailCauseMap.put(COMPANION_IFACE_IN_USE, "COMPANION_IFACE_IN_USE");
        sFailCauseMap.put(IP_ADDRESS_MISMATCH, "IP_ADDRESS_MISMATCH");
        sFailCauseMap.put(IFACE_AND_POL_FAMILY_MISMATCH,
                "IFACE_AND_POL_FAMILY_MISMATCH");
        sFailCauseMap.put(EMM_ACCESS_BARRED_INFINITE_RETRY,
                "EMM_ACCESS_BARRED_INFINITE_RETRY");
        sFailCauseMap.put(AUTH_FAILURE_ON_EMERGENCY_CALL,
                "AUTH_FAILURE_ON_EMERGENCY_CALL");
        sFailCauseMap.put(INVALID_DNS_ADDR, "INVALID_DNS_ADDR");
        sFailCauseMap.put(INVALID_PCSCF_DNS_ADDR, "INVALID_PCSCF_DNS_ADDR");
        sFailCauseMap.put(CALL_PREEMPT_BY_EMERGENCY_APN, "CALL_PREEMPT_BY_EMERGENCY_APN");
        sFailCauseMap.put(UE_INIT_DETACH_OR_DISCONNECT, "UE_INIT_DETACH_OR_DISCONNECT");
        sFailCauseMap.put(MIP_FA_REASON_UNSPECIFIED, "MIP_FA_REASON_UNSPECIFIED");
        sFailCauseMap.put(MIP_FA_ADMIN_PROHIBITED, "MIP_FA_ADMIN_PROHIBITED");
        sFailCauseMap.put(MIP_FA_INSUFFICIENT_RESOURCES, "MIP_FA_INSUFFICIENT_RESOURCES");
        sFailCauseMap.put(MIP_FA_MOBILE_NODE_AUTH_FAILURE, "MIP_FA_MOBILE_NODE_AUTH_FAILURE");
        sFailCauseMap.put(MIP_FA_HA_AUTH_FAILURE, "MIP_FA_HA_AUTH_FAILURE");
        sFailCauseMap.put(MIP_FA_REQ_LIFETIME_TOO_LONG, "MIP_FA_REQ_LIFETIME_TOO_LONG");
        sFailCauseMap.put(MIP_FA_MALFORMED_REQUEST, "MIP_FA_MALFORMED_REQUEST");
        sFailCauseMap.put(MIP_FA_MALFORMED_REPLY, "MIP_FA_MALFORMED_REPLY");
        sFailCauseMap.put(MIP_FA_ENCAPSULATION_UNAVAILABLE, "MIP_FA_ENCAPSULATION_UNAVAILABLE");
        sFailCauseMap.put(MIP_FA_VJHC_UNAVAILABLE, "MIP_FA_VJHC_UNAVAILABLE");
        sFailCauseMap.put(MIP_FA_REV_TUNNEL_UNAVAILABLE, "MIP_FA_REV_TUNNEL_UNAVAILABLE");
        sFailCauseMap.put(MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET,
                "MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET");
        sFailCauseMap.put(MIP_FA_DELIVERY_STYLE_NOT_SUPP, "MIP_FA_DELIVERY_STYLE_NOT_SUPP");
        sFailCauseMap.put(MIP_FA_MISSING_NAI, "MIP_FA_MISSING_NAI");
        sFailCauseMap.put(MIP_FA_MISSING_HA, "MIP_FA_MISSING_HA");
        sFailCauseMap.put(MIP_FA_MISSING_HOME_ADDR, "MIP_FA_MISSING_HOME_ADDR");
        sFailCauseMap.put(MIP_FA_UNKNOWN_CHALLENGE, "MIP_FA_UNKNOWN_CHALLENGE");
        sFailCauseMap.put(MIP_FA_MISSING_CHALLENGE, "MIP_FA_MISSING_CHALLENGE");
        sFailCauseMap.put(MIP_FA_STALE_CHALLENGE, "MIP_FA_STALE_CHALLENGE");
        sFailCauseMap.put(MIP_HA_REASON_UNSPECIFIED, "MIP_HA_REASON_UNSPECIFIED");
        sFailCauseMap.put(MIP_HA_ADMIN_PROHIBITED, "MIP_HA_ADMIN_PROHIBITED");
        sFailCauseMap.put(MIP_HA_INSUFFICIENT_RESOURCES, "MIP_HA_INSUFFICIENT_RESOURCES");
        sFailCauseMap.put(MIP_HA_MOBILE_NODE_AUTH_FAILURE, "MIP_HA_MOBILE_NODE_AUTH_FAILURE");
        sFailCauseMap.put(MIP_HA_FA_AUTH_FAILURE, "MIP_HA_FA_AUTH_FAILURE");
        sFailCauseMap.put(MIP_HA_REGISTRATION_ID_MISMATCH, "MIP_HA_REGISTRATION_ID_MISMATCH");
        sFailCauseMap.put(MIP_HA_MALFORMED_REQUEST, "MIP_HA_MALFORMED_REQUEST");
        sFailCauseMap.put(MIP_HA_UNKNOWN_HA_ADDR, "MIP_HA_UNKNOWN_HA_ADDR");
        sFailCauseMap.put(MIP_HA_REV_TUNNEL_UNAVAILABLE, "MIP_HA_REV_TUNNEL_UNAVAILABLE");
        sFailCauseMap.put(MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET,
                "MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET");
        sFailCauseMap.put(MIP_HA_ENCAPSULATION_UNAVAILABLE, "MIP_HA_ENCAPSULATION_UNAVAILABLE");
        sFailCauseMap.put(CLOSE_IN_PROGRESS, "CLOSE_IN_PROGRESS");
        sFailCauseMap.put(NW_INITIATED_TERMINATION, "NW_INITIATED_TERMINATION");
        sFailCauseMap.put(MODEM_APP_PREEMPTED, "MODEM_APP_PREEMPTED");
        sFailCauseMap.put(ERR_PDN_IPV4_CALL_DISALLOWED, "ERR_PDN_IPV4_CALL_DISALLOWED");
        sFailCauseMap.put(ERR_PDN_IPV4_CALL_THROTTLED, "ERR_PDN_IPV4_CALL_THROTTLED");
        sFailCauseMap.put(ERR_PDN_IPV6_CALL_DISALLOWED, "ERR_PDN_IPV6_CALL_DISALLOWED");
        sFailCauseMap.put(ERR_PDN_IPV6_CALL_THROTTLED, "ERR_PDN_IPV6_CALL_THROTTLED");
        sFailCauseMap.put(MODEM_RESTART, "MODEM_RESTART");
        sFailCauseMap.put(PDP_PPP_NOT_SUPPORTED, "PDP_PPP_NOT_SUPPORTED");
        sFailCauseMap.put(UNPREFERRED_RAT, "UNPREFERRED_RAT");
        sFailCauseMap.put(PHYS_LINK_CLOSE_IN_PROGRESS, "PHYS_LINK_CLOSE_IN_PROGRESS");
        sFailCauseMap.put(APN_PENDING_HANDOVER, "APN_PENDING_HANDOVER");
        sFailCauseMap.put(PROFILE_BEARER_INCOMPATIBLE, "PROFILE_BEARER_INCOMPATIBLE");
        sFailCauseMap.put(SIM_CARD_EVT, "SIM_CARD_EVT");
        sFailCauseMap.put(LPM_OR_PWR_DOWN, "LPM_OR_PWR_DOWN");
        sFailCauseMap.put(APN_DISABLED, "APN_DISABLED");
        sFailCauseMap.put(MAX_PPP_INACTIVITY_TIMER_EXPIRED, "MAX_PPP_INACTIVITY_TIMER_EXPIRED");
        sFailCauseMap.put(IPV6_ADDR_TRANSFER_FAILED, "IPV6_ADDR_TRANSFER_FAILED");
        sFailCauseMap.put(TRAT_SWAP_FAILED, "TRAT_SWAP_FAILED");
        sFailCauseMap.put(EHRPD_TO_HRPD_FALLBACK, "EHRPD_TO_HRPD_FALLBACK");
        sFailCauseMap.put(MIP_CONFIG_FAILURE, "MIP_CONFIG_FAILURE");
        sFailCauseMap.put(PDN_INACTIVITY_TIMER_EXPIRED, "PDN_INACTIVITY_TIMER_EXPIRED");
        sFailCauseMap.put(MAX_V4_CONNECTIONS, "MAX_V4_CONNECTIONS");
        sFailCauseMap.put(MAX_V6_CONNECTIONS, "MAX_V6_CONNECTIONS");
        sFailCauseMap.put(APN_MISMATCH, "APN_MISMATCH");
        sFailCauseMap.put(IP_VERSION_MISMATCH, "IP_VERSION_MISMATCH");
        sFailCauseMap.put(DUN_CALL_DISALLOWED, "DUN_CALL_DISALLOWED");
        sFailCauseMap.put(INTERNAL_EPC_NONEPC_TRANSITION, "INTERNAL_EPC_NONEPC_TRANSITION");
        sFailCauseMap.put(IFACE_IN_USE, "IFACE_IN_USE");
        sFailCauseMap.put(APN_DISALLOWED_ON_ROAMING, "APN_DISALLOWED_ON_ROAMING");
        sFailCauseMap.put(APN_PARAM_CHANGED, "APN_PARAM_CHANGED");
        sFailCauseMap.put(NULL_APN_DISALLOWED, "NULL_APN_DISALLOWED");
        sFailCauseMap.put(THERMAL_MITIGATION, "THERMAL_MITIGATION");
        sFailCauseMap.put(DATA_SETTINGS_DISABLED, "DATA_SETTINGS_DISABLED");
        sFailCauseMap.put(DATA_ROAMING_SETTINGS_DISABLED, "DATA_ROAMING_SETTINGS_DISABLED");
        sFailCauseMap.put(DDS_CALL_ABORT, "DDS_CALL_ABORT");
        sFailCauseMap.put(INVALID_APN_NAME, "INVALID_APN_NAME");
        sFailCauseMap.put(DDS_SWITCH_IN_PROGRESS, "DDS_SWITCH_IN_PROGRESS");
        sFailCauseMap.put(CALL_DISALLOWED_IN_ROAMING, "CALL_DISALLOWED_IN_ROAMING");
        sFailCauseMap.put(NON_IP_NOT_SUPPORTED, "NON_IP_NOT_SUPPORTED");
        sFailCauseMap.put(ERR_PDN_NON_IP_CALL_THROTTLED, "ERR_PDN_NON_IP_CALL_THROTTLED");
        sFailCauseMap.put(ERR_PDN_NON_IP_CALL_DISALLOWED, "ERR_PDN_NON_IP_CALL_DISALLOWED");
        sFailCauseMap.put(CDMA_LOCK, "CDMA_LOCK");
        sFailCauseMap.put(CDMA_INTERCEPT, "CDMA_INTERCEPT");
        sFailCauseMap.put(CDMA_REORDER, "CDMA_REORDER");
        sFailCauseMap.put(CDMA_REL_SO_REJ, "CDMA_REL_SO_REJ");
        sFailCauseMap.put(CDMA_INCOM_CALL, "CDMA_INCOM_CALL");
        sFailCauseMap.put(CDMA_ALERT_STOP, "CDMA_ALERT_STOP");
        sFailCauseMap.put(CHANNEL_ACQUISITION_FAILURE, "CHANNEL_ACQUISITION_FAILURE");
        sFailCauseMap.put(MAX_ACCESS_PROBE, "MAX_ACCESS_PROBE");
        sFailCauseMap.put(CCS_NOT_SUPPORTED_BY_BS, "CCS_NOT_SUPPORTED_BY_BS");
        sFailCauseMap.put(NO_RESPONSE_FROM_BS, "NO_RESPONSE_FROM_BS");
        sFailCauseMap.put(REJECTED_BY_BS, "REJECTED_BY_BS");
        sFailCauseMap.put(CCS_INCOMPATIBLE, "CCS_INCOMPATIBLE");
        sFailCauseMap.put(NO_CDMA_SRV, "NO_CDMA_SRV");
        sFailCauseMap.put(UIM_NOT_PRESENT, "UIM_NOT_PRESENT");
        sFailCauseMap.put(CDMA_RETRY_ORDER, "CDMA_RETRY_ORDER");
        sFailCauseMap.put(ACCESS_BLOCK, "ACCESS_BLOCK");
        sFailCauseMap.put(ACCESS_BLOCK_ALL, "ACCESS_BLOCK_ALL");
        sFailCauseMap.put(IS707B_MAX_ACC, "IS707B_MAX_ACC");
        sFailCauseMap.put(THERMAL_EMERGENCY, "THERMAL_EMERGENCY");
        sFailCauseMap.put(CCS_NOT_ALLOWED, "CCS_NOT_ALLOWED");
        sFailCauseMap.put(INCOM_REJ, "INCOM_REJ");
        sFailCauseMap.put(NO_GATEWAY_SRV, "NO_GATEWAY_SRV");
        sFailCauseMap.put(NO_GPRS_CONTEXT, "NO_GPRS_CONTEXT");
        sFailCauseMap.put(ILLEGAL_MS, "ILLEGAL_MS");
        sFailCauseMap.put(ILLEGAL_ME, "ILLEGAL_ME");
        sFailCauseMap.put(GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED,
                "GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED");
        sFailCauseMap.put(GPRS_SERVICES_NOT_ALLOWED, "GPRS_SERVICES_NOT_ALLOWED");
        sFailCauseMap.put(MS_IDENTITY_CANNOT_BE_DERIVED_BY_THE_NETWORK,
                "MS_IDENTITY_CANNOT_BE_DERIVED_BY_THE_NETWORK");
        sFailCauseMap.put(IMPLICITLY_DETACHED, "IMPLICITLY_DETACHED");
        sFailCauseMap.put(PLMN_NOT_ALLOWED, "PLMN_NOT_ALLOWED");
        sFailCauseMap.put(LA_NOT_ALLOWED, "LA_NOT_ALLOWED");
        sFailCauseMap.put(GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN,
                "GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN");
        sFailCauseMap.put(PDP_DUPLICATE, "PDP_DUPLICATE");
        sFailCauseMap.put(UE_RAT_CHANGE, "UE_RAT_CHANGE");
        sFailCauseMap.put(CONGESTION, "CONGESTION");
        sFailCauseMap.put(NO_PDP_CONTEXT_ACTIVATED, "NO_PDP_CONTEXT_ACTIVATED");
        sFailCauseMap.put(ACCESS_CLASS_DSAC_REJECTION, "ACCESS_CLASS_DSAC_REJECTION");
        sFailCauseMap.put(PDP_ACTIVATE_MAX_RETRY_FAILED, "PDP_ACTIVATE_MAX_RETRY_FAILED");
        sFailCauseMap.put(RAB_FAILURE, "RAB_FAILURE");
        sFailCauseMap.put(ESM_UNKNOWN_EPS_BEARER_CONTEXT, "ESM_UNKNOWN_EPS_BEARER_CONTEXT");
        sFailCauseMap.put(DRB_RELEASED_AT_RRC, "DRB_RELEASED_AT_RRC");
        sFailCauseMap.put(NAS_SIG_CONN_RELEASED, "NAS_SIG_CONN_RELEASED");
        sFailCauseMap.put(EMM_DETACHED, "EMM_DETACHED");
        sFailCauseMap.put(EMM_ATTACH_FAILED, "EMM_ATTACH_FAILED");
        sFailCauseMap.put(EMM_ATTACH_STARTED, "EMM_ATTACH_STARTED");
        sFailCauseMap.put(LTE_NAS_SERVICE_REQ_FAILED, "LTE_NAS_SERVICE_REQ_FAILED");
        sFailCauseMap.put(ESM_ACTIVE_DEDICATED_BEARER_REACTIVATED_BY_NW,
                "ESM_ACTIVE_DEDICATED_BEARER_REACTIVATED_BY_NW");
        sFailCauseMap.put(ESM_LOWER_LAYER_FAILURE, "ESM_LOWER_LAYER_FAILURE");
        sFailCauseMap.put(ESM_SYNC_UP_WITH_NW, "ESM_SYNC_UP_WITH_NW");
        sFailCauseMap.put(ESM_NW_ACTIVATED_DED_BEARER_WITH_ID_OF_DEF_BEARER,
                "ESM_NW_ACTIVATED_DED_BEARER_WITH_ID_OF_DEF_BEARER");
        sFailCauseMap.put(ESM_BAD_OTA_MESSAGE, "ESM_BAD_OTA_MESSAGE");
        sFailCauseMap.put(ESM_DS_REJECTED_THE_CALL, "ESM_DS_REJECTED_THE_CALL");
        sFailCauseMap.put(ESM_CONTEXT_TRANSFERED_DUE_TO_IRAT,
                "ESM_CONTEXT_TRANSFERED_DUE_TO_IRAT");
        sFailCauseMap.put(DS_EXPLICIT_DEACT, "DS_EXPLICIT_DEACT");
        sFailCauseMap.put(ESM_LOCAL_CAUSE_NONE, "ESM_LOCAL_CAUSE_NONE");
        sFailCauseMap.put(LTE_NAS_SERVICE_REQ_FAILED_NO_THROTTLE,
                "LTE_NAS_SERVICE_REQ_FAILED_NO_THROTTLE");
        sFailCauseMap.put(ACCESS_CONTROL_LIST_CHECK_FAILURE, "ACCESS_CONTROL_LIST_CHECK_FAILURE");
        sFailCauseMap.put(LTE_NAS_SERVICE_REQ_FAILED_DS_DISALLOW,
                "LTE_NAS_SERVICE_REQ_FAILED_DS_DISALLOW");
        sFailCauseMap.put(EMM_T3417_EXPIRED, "EMM_T3417_EXPIRED");
        sFailCauseMap.put(EMM_T3417_EXT_EXPIRED, "EMM_T3417_EXT_EXPIRED");
        sFailCauseMap.put(LRRC_UL_DATA_CNF_FAILURE_TXN, "LRRC_UL_DATA_CNF_FAILURE_TXN");
        sFailCauseMap.put(LRRC_UL_DATA_CNF_FAILURE_HO, "LRRC_UL_DATA_CNF_FAILURE_HO");
        sFailCauseMap.put(LRRC_UL_DATA_CNF_FAILURE_CONN_REL, "LRRC_UL_DATA_CNF_FAILURE_CONN_REL");
        sFailCauseMap.put(LRRC_UL_DATA_CNF_FAILURE_RLF, "LRRC_UL_DATA_CNF_FAILURE_RLF");
        sFailCauseMap.put(LRRC_UL_DATA_CNF_FAILURE_CTRL_NOT_CONN,
                "LRRC_UL_DATA_CNF_FAILURE_CTRL_NOT_CONN");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE, "LRRC_CONN_EST_FAILURE");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_ABORTED, "LRRC_CONN_EST_FAILURE_ABORTED");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_ACCESS_BARRED,
                "LRRC_CONN_EST_FAILURE_ACCESS_BARRED");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_CELL_RESEL,
                "LRRC_CONN_EST_FAILURE_CELL_RESEL");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_CONFIG_FAILURE,
                "LRRC_CONN_EST_FAILURE_CONFIG_FAILURE");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_TIMER_EXPIRED,
                "LRRC_CONN_EST_FAILURE_TIMER_EXPIRED");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_LINK_FAILURE,
                "LRRC_CONN_EST_FAILURE_LINK_FAILURE");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_NOT_CAMPED, "LRRC_CONN_EST_FAILURE_NOT_CAMPED");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_SI_FAILURE, "LRRC_CONN_EST_FAILURE_SI_FAILURE");
        sFailCauseMap.put(LRRC_CONN_EST_FAILURE_CONN_REJECT, "LRRC_CONN_EST_FAILURE_CONN_REJECT");
        sFailCauseMap.put(LRRC_CONN_REL_NORMAL, "LRRC_CONN_REL_NORMAL");
        sFailCauseMap.put(LRRC_CONN_REL_RLF, "LRRC_CONN_REL_RLF");
        sFailCauseMap.put(LRRC_CONN_REL_CRE_FAILURE, "LRRC_CONN_REL_CRE_FAILURE");
        sFailCauseMap.put(LRRC_CONN_REL_OOS_DURING_CRE, "LRRC_CONN_REL_OOS_DURING_CRE");
        sFailCauseMap.put(LRRC_CONN_REL_ABORTED, "LRRC_CONN_REL_ABORTED");
        sFailCauseMap.put(LRRC_CONN_REL_SIB_READ_ERROR, "LRRC_CONN_REL_SIB_READ_ERROR");
        sFailCauseMap.put(DETACH_WITH_REATTACH_LTE_NW_DETACH,
                "DETACH_WITH_REATTACH_LTE_NW_DETACH");
        sFailCauseMap.put(DETACH_WITHOUT_REATTACH_LTE_NW_DETACH,
                "DETACH_WITHOUT_REATTACH_LTE_NW_DETACH");
        sFailCauseMap.put(ESM_PROC_TIME_OUT, "ESM_PROC_TIME_OUT");
        sFailCauseMap.put(INVALID_CONNECTION_ID, "INVALID_CONNECTION_ID");
        sFailCauseMap.put(INVALID_NSAPI, "INVALID_NSAPI");
        sFailCauseMap.put(INVALID_PRI_NSAPI, "INVALID_PRI_NSAPI");
        sFailCauseMap.put(INVALID_FIELD, "INVALID_FIELD");
        sFailCauseMap.put(RAB_SETUP_FAILURE, "RAB_SETUP_FAILURE");
        sFailCauseMap.put(PDP_ESTABLISH_MAX_TIMEOUT, "PDP_ESTABLISH_MAX_TIMEOUT");
        sFailCauseMap.put(PDP_MODIFY_MAX_TIMEOUT, "PDP_MODIFY_MAX_TIMEOUT");
        sFailCauseMap.put(PDP_INACTIVE_MAX_TIMEOUT, "PDP_INACTIVE_MAX_TIMEOUT");
        sFailCauseMap.put(PDP_LOWERLAYER_ERROR, "PDP_LOWERLAYER_ERROR");
        sFailCauseMap.put(PDP_MODIFY_COLLISION, "PDP_MODIFY_COLLISION");
        sFailCauseMap.put(SM_NO_RADIO_AVAILABLE, "SM_NO_RADIO_AVAILABLE");
        sFailCauseMap.put(SM_ABORT_SERVICE_NOT_AVAILABLE, "SM_ABORT_SERVICE_NOT_AVAILABLE");
        sFailCauseMap.put(MESSAGE_EXCEED_MAX_L2_LIMIT, "MESSAGE_EXCEED_MAX_L2_LIMIT");
        sFailCauseMap.put(SM_NAS_SRV_REQ_FAILURE, "SM_NAS_SRV_REQ_FAILURE");
        sFailCauseMap.put(RRC_CONN_EST_FAILURE_REQ_ERROR, "RRC_CONN_EST_FAILURE_REQ_ERROR");
        sFailCauseMap.put(RRC_CONN_EST_FAILURE_TAI_CHANGE, "RRC_CONN_EST_FAILURE_TAI_CHANGE");
        sFailCauseMap.put(RRC_CONN_EST_FAILURE_RF_UNAVAILABLE,
                "RRC_CONN_EST_FAILURE_RF_UNAVAILABLE");
        sFailCauseMap.put(RRC_CONN_REL_ABORTED_IRAT_SUCCESS, "RRC_CONN_REL_ABORTED_IRAT_SUCCESS");
        sFailCauseMap.put(RRC_CONN_REL_RLF_SEC_NOT_ACTIVE, "RRC_CONN_REL_RLF_SEC_NOT_ACTIVE");
        sFailCauseMap.put(RRC_CONN_REL_IRAT_TO_LTE_ABORTED, "RRC_CONN_REL_IRAT_TO_LTE_ABORTED");
        sFailCauseMap.put(RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_SUCCESS,
                "RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_SUCCESS");
        sFailCauseMap.put(RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_ABORTED,
                "RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_ABORTED");
        sFailCauseMap.put(IMSI_UNKNOWN_IN_HSS, "IMSI_UNKNOWN_IN_HSS");
        sFailCauseMap.put(IMEI_NOT_ACCEPTED, "IMEI_NOT_ACCEPTED");
        sFailCauseMap.put(EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED,
                "EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED");
        sFailCauseMap.put(EPS_SERVICES_NOT_ALLOWED_IN_PLMN, "EPS_SERVICES_NOT_ALLOWED_IN_PLMN");
        sFailCauseMap.put(MSC_TEMPORARILY_NOT_REACHABLE, "MSC_TEMPORARILY_NOT_REACHABLE");
        sFailCauseMap.put(CS_DOMAIN_NOT_AVAILABLE, "CS_DOMAIN_NOT_AVAILABLE");
        sFailCauseMap.put(ESM_FAILURE, "ESM_FAILURE");
        sFailCauseMap.put(MAC_FAILURE, "MAC_FAILURE");
        sFailCauseMap.put(SYNCH_FAILURE, "SYNCH_FAILURE");
        sFailCauseMap.put(UE_SECURITY_CAPABILITIES_MISMATCH, "UE_SECURITY_CAPABILITIES_MISMATCH");
        sFailCauseMap.put(SECURITY_MODE_REJ_UNSPECIFIED, "SECURITY_MODE_REJ_UNSPECIFIED");
        sFailCauseMap.put(NON_EPS_AUTH_UNACCEPTABLE, "NON_EPS_AUTH_UNACCEPTABLE");
        sFailCauseMap.put(CS_FALLBACK_CALL_EST_NOT_ALLOWED, "CS_FALLBACK_CALL_EST_NOT_ALLOWED");
        sFailCauseMap.put(NO_EPS_BEARER_CONTEXT_ACTIVATED, "NO_EPS_BEARER_CONTEXT_ACTIVATED");
        sFailCauseMap.put(EMM_INVALID_STATE, "EMM_INVALID_STATE");
        sFailCauseMap.put(NAS_LAYER_FAILURE, "NAS_LAYER_FAILURE");
        sFailCauseMap.put(MULTI_PDN_NOT_ALLOWED, "MULTI_PDN_NOT_ALLOWED");
        sFailCauseMap.put(EMBMS_NOT_ENABLED, "EMBMS_NOT_ENABLED");
        sFailCauseMap.put(PENDING_REDIAL_CALL_CLEANUP, "PENDING_REDIAL_CALL_CLEANUP");
        sFailCauseMap.put(EMBMS_REGULAR_DEACTIVATION, "EMBMS_REGULAR_DEACTIVATION");
        sFailCauseMap.put(TLB_REGULAR_DEACTIVATION, "TLB_REGULAR_DEACTIVATION");
        sFailCauseMap.put(LOWER_LAYER_REGISTRATION_FAILURE, "LOWER_LAYER_REGISTRATION_FAILURE");
        sFailCauseMap.put(DETACH_EPS_SERVICES_NOT_ALLOWED, "DETACH_EPS_SERVICES_NOT_ALLOWED");
        sFailCauseMap.put(SM_INTERNAL_PDP_DEACTIVATION, "SM_INTERNAL_PDP_DEACTIVATION");
        sFailCauseMap.put(CD_GEN_OR_BUSY, "CD_GEN_OR_BUSY");
        sFailCauseMap.put(CD_BILL_OR_AUTH, "CD_BILL_OR_AUTH");
        sFailCauseMap.put(HDR_CHANGED, "HDR_CHANGED");
        sFailCauseMap.put(HDR_EXITED, "HDR_EXITED");
        sFailCauseMap.put(HDR_NO_SESSION, "HDR_NO_SESSION");
        sFailCauseMap.put(HDR_ORIG_DURING_GPS_FIX, "HDR_ORIG_DURING_GPS_FIX");
        sFailCauseMap.put(HDR_CS_TIMEOUT, "HDR_CS_TIMEOUT");
        sFailCauseMap.put(COLLOC_ACQ_FAIL, "COLLOC_ACQ_FAIL");
        sFailCauseMap.put(OTASP_COMMIT_IN_PROG, "OTASP_COMMIT_IN_PROG");
        sFailCauseMap.put(NO_HYBR_HDR_SRV, "NO_HYBR_HDR_SRV");
        sFailCauseMap.put(HDR_NO_LOCK_GRANTED, "HDR_NO_LOCK_GRANTED");
        sFailCauseMap.put(HOLD_OTHER_IN_PROG, "HOLD_OTHER_IN_PROG");
        sFailCauseMap.put(HDR_FADE, "HDR_FADE");
        sFailCauseMap.put(HDR_ACC_FAIL, "HDR_ACC_FAIL");
        sFailCauseMap.put(UNSUPPORTED_1X_PREV, "UNSUPPORTED_1X_PREV");
        sFailCauseMap.put(LOCAL_END, "LOCAL_END");
        sFailCauseMap.put(NO_SRV, "NO_SRV");
        sFailCauseMap.put(FADE, "FADE");
        sFailCauseMap.put(REL_NORMAL, "REL_NORMAL");
        sFailCauseMap.put(ACC_IN_PROG, "ACC_IN_PROG");
        sFailCauseMap.put(ACC_FAIL, "ACC_FAIL");
        sFailCauseMap.put(REDIR_OR_HANDOFF, "REDIR_OR_HANDOFF");
        sFailCauseMap.put(EMERGENCY_MODE, "EMERGENCY_MODE");
        sFailCauseMap.put(PHONE_IN_USE, "PHONE_IN_USE");
        sFailCauseMap.put(INVALID_MODE, "INVALID_MODE");
        sFailCauseMap.put(INVALID_SIM_STATE, "INVALID_SIM_STATE");
        sFailCauseMap.put(NO_COLLOC_HDR, "NO_COLLOC_HDR");
        sFailCauseMap.put(EMM_DETACHED_PSM, "EMM_DETACHED_PSM");
        sFailCauseMap.put(DUAL_SWITCH, "DUAL_SWITCH");
        sFailCauseMap.put(PPP_TIMEOUT, "PPP_TIMEOUT");
        sFailCauseMap.put(PPP_AUTH_FAILURE, "PPP_AUTH_FAILURE");
        sFailCauseMap.put(PPP_OPTION_MISMATCH, "PPP_OPTION_MISMATCH");
        sFailCauseMap.put(PPP_PAP_FAILURE, "PPP_PAP_FAILURE");
        sFailCauseMap.put(PPP_CHAP_FAILURE, "PPP_CHAP_FAILURE");
        sFailCauseMap.put(PPP_ERR_CLOSE_IN_PROGRESS, "PPP_ERR_CLOSE_IN_PROGRESS");
        sFailCauseMap.put(EHRPD_SUBS_LIMITED_TO_V4, "EHRPD_SUBS_LIMITED_TO_V4");
        sFailCauseMap.put(EHRPD_SUBS_LIMITED_TO_V6, "EHRPD_SUBS_LIMITED_TO_V6");
        sFailCauseMap.put(EHRPD_VSNCP_TIMEOUT, "EHRPD_VSNCP_TIMEOUT");
        sFailCauseMap.put(EHRPD_VSNCP_GEN_ERROR, "EHRPD_VSNCP_GEN_ERROR");
        sFailCauseMap.put(EHRPD_VSNCP_UNAUTH_APN, "EHRPD_VSNCP_UNAUTH_APN");
        sFailCauseMap.put(EHRPD_VSNCP_PDN_LIMIT_EXCEED, "EHRPD_VSNCP_PDN_LIMIT_EXCEED");
        sFailCauseMap.put(EHRPD_VSNCP_NO_PDN_GW, "EHRPD_VSNCP_NO_PDN_GW");
        sFailCauseMap.put(EHRPD_VSNCP_PDN_GW_UNREACH, "EHRPD_VSNCP_PDN_GW_UNREACH");
        sFailCauseMap.put(EHRPD_VSNCP_PDN_GW_REJ, "EHRPD_VSNCP_PDN_GW_REJ");
        sFailCauseMap.put(EHRPD_VSNCP_INSUFF_PARAM, "EHRPD_VSNCP_INSUFF_PARAM");
        sFailCauseMap.put(EHRPD_VSNCP_RESOURCE_UNAVAIL, "EHRPD_VSNCP_RESOURCE_UNAVAIL");
        sFailCauseMap.put(EHRPD_VSNCP_ADMIN_PROHIBIT, "EHRPD_VSNCP_ADMIN_PROHIBIT");
        sFailCauseMap.put(EHRPD_VSNCP_PDN_ID_IN_USE, "EHRPD_VSNCP_PDN_ID_IN_USE");
        sFailCauseMap.put(EHRPD_VSNCP_SUBSCR_LIMITATION, "EHRPD_VSNCP_SUBSCR_LIMITATION");
        sFailCauseMap.put(EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN,
                "EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN");
        sFailCauseMap.put(EHRPD_VSNCP_RECONNECT_NOT_ALLOWED, "EHRPD_VSNCP_RECONNECT_NOT_ALLOWED");
        sFailCauseMap.put(IPV6_PREFIX_UNAVAILABLE, "IPV6_PREFIX_UNAVAILABLE");
        sFailCauseMap.put(HANDOFF_PREF_SYS_BACK_TO_SRAT, "HANDOFF_PREF_SYS_BACK_TO_SRAT");
        sFailCauseMap.put(OEM_DCFAILCAUSE_1, "OEM_DCFAILCAUSE_1");
        sFailCauseMap.put(OEM_DCFAILCAUSE_2, "OEM_DCFAILCAUSE_2");
        sFailCauseMap.put(OEM_DCFAILCAUSE_3, "OEM_DCFAILCAUSE_3");
        sFailCauseMap.put(OEM_DCFAILCAUSE_4, "OEM_DCFAILCAUSE_4");
        sFailCauseMap.put(OEM_DCFAILCAUSE_5, "OEM_DCFAILCAUSE_5");
        sFailCauseMap.put(OEM_DCFAILCAUSE_6, "OEM_DCFAILCAUSE_6");
        sFailCauseMap.put(OEM_DCFAILCAUSE_7, "OEM_DCFAILCAUSE_7");
        sFailCauseMap.put(OEM_DCFAILCAUSE_8, "OEM_DCFAILCAUSE_8");
        sFailCauseMap.put(OEM_DCFAILCAUSE_9, "OEM_DCFAILCAUSE_9");
        sFailCauseMap.put(OEM_DCFAILCAUSE_10, "OEM_DCFAILCAUSE_10");
        sFailCauseMap.put(OEM_DCFAILCAUSE_11, "OEM_DCFAILCAUSE_11");
        sFailCauseMap.put(OEM_DCFAILCAUSE_12, "OEM_DCFAILCAUSE_12");
        sFailCauseMap.put(OEM_DCFAILCAUSE_13, "OEM_DCFAILCAUSE_13");
        sFailCauseMap.put(OEM_DCFAILCAUSE_14, "OEM_DCFAILCAUSE_14");
        sFailCauseMap.put(OEM_DCFAILCAUSE_15, "OEM_DCFAILCAUSE_15");
        sFailCauseMap.put(REGISTRATION_FAIL, "REGISTRATION_FAIL");
        sFailCauseMap.put(GPRS_REGISTRATION_FAIL, "GPRS_REGISTRATION_FAIL");
        sFailCauseMap.put(SIGNAL_LOST, "SIGNAL_LOST");
        sFailCauseMap.put(PREF_RADIO_TECH_CHANGED, "PREF_RADIO_TECH_CHANGED");
        sFailCauseMap.put(RADIO_POWER_OFF, "RADIO_POWER_OFF");
        sFailCauseMap.put(TETHERED_CALL_ACTIVE, "TETHERED_CALL_ACTIVE");
        sFailCauseMap.put(ERROR_UNSPECIFIED, "ERROR_UNSPECIFIED");
        sFailCauseMap.put(UNKNOWN, "UNKNOWN");
        sFailCauseMap.put(RADIO_NOT_AVAILABLE, "RADIO_NOT_AVAILABLE");
        sFailCauseMap.put(UNACCEPTABLE_NETWORK_PARAMETER,
                "UNACCEPTABLE_NETWORK_PARAMETER");
        sFailCauseMap.put(CONNECTION_TO_DATACONNECTIONAC_BROKEN,
                "CONNECTION_TO_DATACONNECTIONAC_BROKEN");
        sFailCauseMap.put(LOST_CONNECTION, "LOST_CONNECTION");
        sFailCauseMap.put(RESET_BY_FRAMEWORK, "RESET_BY_FRAMEWORK");
    }

    /**
     * Map of subId -> set of data call setup permanent failure for the carrier.
     */
    private static final HashMap<Integer, Set<Integer>> sPermanentFailureCache =
            new HashMap<>();

    /**
     * Returns whether or not the fail cause is a failure that requires a modem restart
     *
     * @param context device context
     * @param cause data disconnect cause
     * @param subId subscription index
     * @return true if the fail cause code needs platform to trigger a modem restart.
     */
    public static boolean isRadioRestartFailure(@NonNull Context context, @FailCause int cause,
                                                int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForSubId(subId);

            if (b != null) {
                if (cause == REGULAR_DEACTIVATION
                        && b.getBoolean(CarrierConfigManager
                        .KEY_RESTART_RADIO_ON_PDP_FAIL_REGULAR_DEACTIVATION_BOOL)) {
                    // This is for backward compatibility support. We need to continue support this
                    // old configuration until it gets removed in the future.
                    return true;
                }
                // Check the current configurations.
                int[] causeCodes = b.getIntArray(CarrierConfigManager
                        .KEY_RADIO_RESTART_FAILURE_CAUSES_INT_ARRAY);
                if (causeCodes != null) {
                    return Arrays.stream(causeCodes).anyMatch(i -> i == cause);
                }
            }
        }

        return false;
    }

    public static boolean isPermanentFailure(@NonNull Context context, @FailCause int failCause,
                                             int subId) {
        synchronized (sPermanentFailureCache) {

            Set<Integer> permanentFailureSet = sPermanentFailureCache.get(subId);

            // In case of cache miss, we need to look up the settings from carrier config.
            if (permanentFailureSet == null) {
                // Retrieve the permanent failure from carrier config
                CarrierConfigManager configManager = (CarrierConfigManager)
                        context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (configManager != null) {
                    PersistableBundle b = configManager.getConfigForSubId(subId);
                    if (b != null) {
                        String[] permanentFailureStrings = b.getStringArray(CarrierConfigManager.
                                KEY_CARRIER_DATA_CALL_PERMANENT_FAILURE_STRINGS);
                        if (permanentFailureStrings != null) {
                            permanentFailureSet = new HashSet<>();
                            for (Map.Entry<Integer, String> e : sFailCauseMap.entrySet()) {
                                if (ArrayUtils.contains(permanentFailureStrings, e.getValue())) {
                                    permanentFailureSet.add(e.getKey());
                                }
                            }
                        }
                    }
                }

                // If we are not able to find the configuration from carrier config, use the default
                // ones.
                if (permanentFailureSet == null) {
                    permanentFailureSet = new HashSet<Integer>() {
                        {
                            add(OPERATOR_BARRED);
                            add(MISSING_UNKNOWN_APN);
                            add(UNKNOWN_PDP_ADDRESS_TYPE);
                            add(USER_AUTHENTICATION);
                            add(ACTIVATION_REJECT_GGSN);
                            add(SERVICE_OPTION_NOT_SUPPORTED);
                            add(SERVICE_OPTION_NOT_SUBSCRIBED);
                            add(NSAPI_IN_USE);
                            add(ONLY_IPV4_ALLOWED);
                            add(ONLY_IPV6_ALLOWED);
                            add(ONLY_IPV4V6_ALLOWED);
                            add(ONLY_NON_IP_ALLOWED);
                            add(PROTOCOL_ERRORS);
                            add(MIP_FA_REASON_UNSPECIFIED);
                            add(MIP_FA_ADMIN_PROHIBITED);
                            add(MIP_FA_INSUFFICIENT_RESOURCES);
                            add(MIP_FA_MOBILE_NODE_AUTH_FAILURE);
                            add(MIP_FA_HA_AUTH_FAILURE);
                            add(MIP_FA_MALFORMED_REQUEST);
                            add(MIP_FA_MALFORMED_REPLY);
                            add(MIP_FA_ENCAPSULATION_UNAVAILABLE);
                            add(MIP_FA_VJHC_UNAVAILABLE);
                            add(MIP_FA_REV_TUNNEL_UNAVAILABLE);
                            add(MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET);
                            add(MIP_FA_DELIVERY_STYLE_NOT_SUPP);
                            add(MIP_FA_MISSING_NAI);
                            add(MIP_FA_MISSING_HA);
                            add(MIP_FA_MISSING_HOME_ADDR);
                            add(MIP_FA_MISSING_CHALLENGE);
                            add(MIP_HA_REASON_UNSPECIFIED);
                            add(MIP_HA_ADMIN_PROHIBITED);
                            add(MIP_HA_INSUFFICIENT_RESOURCES);
                            add(MIP_HA_MOBILE_NODE_AUTH_FAILURE);
                            add(MIP_HA_FA_AUTH_FAILURE);
                            add(MIP_HA_MALFORMED_REQUEST);
                            add(MIP_HA_UNKNOWN_HA_ADDR);
                            add(MIP_HA_REV_TUNNEL_UNAVAILABLE);
                            add(MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET);
                            add(MIP_HA_ENCAPSULATION_UNAVAILABLE);
                            add(NW_INITIATED_TERMINATION);
                            add(MIP_CONFIG_FAILURE);
                            add(NON_IP_NOT_SUPPORTED);
                            add(CDMA_LOCK);
                            add(CDMA_REL_SO_REJ);
                            add(CCS_NOT_SUPPORTED_BY_BS);
                            add(REJECTED_BY_BS);
                            add(CCS_INCOMPATIBLE);
                            add(UIM_NOT_PRESENT);
                            add(THERMAL_EMERGENCY);
                            add(ILLEGAL_MS);
                            add(ILLEGAL_ME);
                            add(GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED);
                            add(GPRS_SERVICES_NOT_ALLOWED);
                            add(PLMN_NOT_ALLOWED);
                            add(LA_NOT_ALLOWED);
                            add(GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN);
                            add(IMEI_NOT_ACCEPTED);
                            add(EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED);
                            add(EPS_SERVICES_NOT_ALLOWED_IN_PLMN);
                            add(CD_BILL_OR_AUTH);
                            add(EMERGENCY_MODE);
                            add(INVALID_MODE);
                            add(INVALID_SIM_STATE);
                            add(EMM_DETACHED_PSM);
                            add(PPP_AUTH_FAILURE);
                            add(PPP_OPTION_MISMATCH);
                            add(PPP_PAP_FAILURE);
                            add(PPP_CHAP_FAILURE);
                            add(EHRPD_SUBS_LIMITED_TO_V4);
                            add(EHRPD_SUBS_LIMITED_TO_V6);
                            add(EHRPD_VSNCP_GEN_ERROR);
                            add(EHRPD_VSNCP_UNAUTH_APN);
                            add(EHRPD_VSNCP_PDN_LIMIT_EXCEED);
                            add(EHRPD_VSNCP_NO_PDN_GW);
                            add(EHRPD_VSNCP_PDN_GW_UNREACH);
                            add(EHRPD_VSNCP_PDN_GW_REJ);
                            add(EHRPD_VSNCP_INSUFF_PARAM);
                            add(EHRPD_VSNCP_RESOURCE_UNAVAIL);
                            add(EHRPD_VSNCP_ADMIN_PROHIBIT);
                            add(EHRPD_VSNCP_SUBSCR_LIMITATION);
                            add(EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN);
                            add(EHRPD_VSNCP_RECONNECT_NOT_ALLOWED);
                            add(PROTOCOL_ERRORS);
                            add(RADIO_POWER_OFF);
                            add(TETHERED_CALL_ACTIVE);
                            add(RADIO_NOT_AVAILABLE);
                            add(UNACCEPTABLE_NETWORK_PARAMETER);
                            add(SIGNAL_LOST);
                        }
                    };
                }

                sPermanentFailureCache.put(subId, permanentFailureSet);
            }

            return permanentFailureSet.contains(failCause);
        }
    }

    public static boolean isEventLoggable(@FailCause int dataFailCause) {
        return (dataFailCause == OPERATOR_BARRED) || (dataFailCause == INSUFFICIENT_RESOURCES)
                || (dataFailCause == UNKNOWN_PDP_ADDRESS_TYPE)
                || (dataFailCause == USER_AUTHENTICATION)
                || (dataFailCause == ACTIVATION_REJECT_GGSN)
                || (dataFailCause == ACTIVATION_REJECT_UNSPECIFIED)
                || (dataFailCause == SERVICE_OPTION_NOT_SUBSCRIBED)
                || (dataFailCause == SERVICE_OPTION_NOT_SUPPORTED)
                || (dataFailCause == SERVICE_OPTION_OUT_OF_ORDER)
                || (dataFailCause == NSAPI_IN_USE)
                || (dataFailCause == ONLY_IPV4_ALLOWED)
                || (dataFailCause == ONLY_IPV6_ALLOWED)
                || (dataFailCause == ONLY_IPV4V6_ALLOWED)
                || (dataFailCause == ONLY_NON_IP_ALLOWED)
                || (dataFailCause == PROTOCOL_ERRORS)
                || (dataFailCause == MIP_FA_REASON_UNSPECIFIED)
                || (dataFailCause == MIP_FA_ADMIN_PROHIBITED)
                || (dataFailCause == MIP_FA_INSUFFICIENT_RESOURCES)
                || (dataFailCause == MIP_FA_MOBILE_NODE_AUTH_FAILURE)
                || (dataFailCause == MIP_FA_HA_AUTH_FAILURE)
                || (dataFailCause == MIP_FA_MALFORMED_REQUEST)
                || (dataFailCause == MIP_FA_MALFORMED_REPLY)
                || (dataFailCause == MIP_FA_ENCAPSULATION_UNAVAILABLE)
                || (dataFailCause == MIP_FA_VJHC_UNAVAILABLE)
                || (dataFailCause == MIP_FA_REV_TUNNEL_UNAVAILABLE)
                || (dataFailCause == MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET)
                || (dataFailCause == MIP_FA_DELIVERY_STYLE_NOT_SUPP)
                || (dataFailCause == MIP_FA_MISSING_NAI)
                || (dataFailCause == MIP_FA_MISSING_HA)
                || (dataFailCause == MIP_FA_MISSING_HOME_ADDR)
                || (dataFailCause == MIP_FA_MISSING_CHALLENGE)
                || (dataFailCause == MIP_HA_REASON_UNSPECIFIED)
                || (dataFailCause == MIP_HA_ADMIN_PROHIBITED)
                || (dataFailCause == MIP_HA_INSUFFICIENT_RESOURCES)
                || (dataFailCause == MIP_HA_MOBILE_NODE_AUTH_FAILURE)
                || (dataFailCause == MIP_HA_FA_AUTH_FAILURE)
                || (dataFailCause == MIP_HA_MALFORMED_REQUEST)
                || (dataFailCause == MIP_HA_UNKNOWN_HA_ADDR)
                || (dataFailCause == MIP_HA_REV_TUNNEL_UNAVAILABLE)
                || (dataFailCause == MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET)
                || (dataFailCause == MIP_HA_ENCAPSULATION_UNAVAILABLE)
                || (dataFailCause == NW_INITIATED_TERMINATION)
                || (dataFailCause == MIP_CONFIG_FAILURE)
                || (dataFailCause == NON_IP_NOT_SUPPORTED)
                || (dataFailCause == CDMA_LOCK)
                || (dataFailCause == CDMA_REL_SO_REJ)
                || (dataFailCause == CCS_NOT_SUPPORTED_BY_BS)
                || (dataFailCause == REJECTED_BY_BS)
                || (dataFailCause == CCS_INCOMPATIBLE)
                || (dataFailCause == UIM_NOT_PRESENT)
                || (dataFailCause == THERMAL_EMERGENCY)
                || (dataFailCause == ILLEGAL_MS)
                || (dataFailCause == ILLEGAL_ME)
                || (dataFailCause == GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED)
                || (dataFailCause == GPRS_SERVICES_NOT_ALLOWED)
                || (dataFailCause == PLMN_NOT_ALLOWED)
                || (dataFailCause == LA_NOT_ALLOWED)
                || (dataFailCause == GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN)
                || (dataFailCause == IMEI_NOT_ACCEPTED)
                || (dataFailCause == EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED)
                || (dataFailCause == EPS_SERVICES_NOT_ALLOWED_IN_PLMN)
                || (dataFailCause == CD_BILL_OR_AUTH)
                || (dataFailCause == EMERGENCY_MODE)
                || (dataFailCause == INVALID_MODE)
                || (dataFailCause == INVALID_SIM_STATE)
                || (dataFailCause == EMM_DETACHED_PSM)
                || (dataFailCause == PPP_AUTH_FAILURE)
                || (dataFailCause == PPP_OPTION_MISMATCH)
                || (dataFailCause == PPP_PAP_FAILURE)
                || (dataFailCause == PPP_CHAP_FAILURE)
                || (dataFailCause == EHRPD_SUBS_LIMITED_TO_V4)
                || (dataFailCause == EHRPD_SUBS_LIMITED_TO_V6)
                || (dataFailCause == EHRPD_VSNCP_GEN_ERROR)
                || (dataFailCause == EHRPD_VSNCP_UNAUTH_APN)
                || (dataFailCause == EHRPD_VSNCP_PDN_LIMIT_EXCEED)
                || (dataFailCause == EHRPD_VSNCP_NO_PDN_GW)
                || (dataFailCause == EHRPD_VSNCP_PDN_GW_UNREACH)
                || (dataFailCause == EHRPD_VSNCP_PDN_GW_REJ)
                || (dataFailCause == EHRPD_VSNCP_INSUFF_PARAM)
                || (dataFailCause == EHRPD_VSNCP_RESOURCE_UNAVAIL)
                || (dataFailCause == EHRPD_VSNCP_ADMIN_PROHIBIT)
                || (dataFailCause == EHRPD_VSNCP_SUBSCR_LIMITATION)
                || (dataFailCause == EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN)
                || (dataFailCause == EHRPD_VSNCP_RECONNECT_NOT_ALLOWED)
                || (dataFailCause == PROTOCOL_ERRORS)
                || (dataFailCause == SIGNAL_LOST)
                || (dataFailCause == RADIO_POWER_OFF)
                || (dataFailCause == TETHERED_CALL_ACTIVE)
                || (dataFailCause == UNACCEPTABLE_NETWORK_PARAMETER);
    }

    public static String toString(@FailCause int dataFailCause) {
        int cause = getFailCause(dataFailCause);
        return (cause == UNKNOWN) ? "UNKNOWN(" + dataFailCause + ")" : sFailCauseMap.get(cause);
    }

    public static int getFailCause(@FailCause int failCause) {
        if (sFailCauseMap.containsKey(failCause)) {
            return failCause;
        } else {
            return UNKNOWN;
        }
    }
}
