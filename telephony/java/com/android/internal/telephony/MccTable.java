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

package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.content.Context;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;

/**
 * The table below is built from two resources:
 *
 * 1) ITU "Mobile Network Code (MNC) for the international
 *   identification plan for mobile terminals and mobile users"
 *   which is available as an annex to the ITU operational bulletin
 *   available here: http://www.itu.int/itu-t/bulletin/annex.html
 *
 * 2) The ISO 3166 country codes list, available here:
 *    http://www.iso.org/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/index.html
 *
 * This table was verified (28 Aug 2009) against
 * http://en.wikipedia.org/wiki/List_of_mobile_country_codes with the
 * only unresolved discrepancy being that this list has an extra entry
 * (461) for China.
 *
 * TODO: Complete the mappings for timezones and language/locale codes.
 *
 * The actual table data used in the Java code is generated from the
 * below Python code for efficiency.  The information is expected to
 * be static, but if changes are required, the table in the python
 * code can be modified and the trailing code run to re-generate the
 * tables that are to be used by Java.

mcc_table = [
  (202, 'gr', 2, 'Europe/Athens', 'el', 13, 'Greece'),
  (204, 'nl', 2, 'Europe/Amsterdam', 'nl', 13, 'Netherlands (Kingdom of the)'),
  (206, 'be', 2, 'Europe/Brussels', '', 13, 'Belgium'),
  (208, 'fr', 2, 'Europe/Paris', 'fr', 13, 'France'),
  (212, 'mc', 2, 'Europe/Monaco', 'fr', 13, 'Monaco (Principality of)'),
  (213, 'ad', 2, 'Europe/Andorra', 'ca', 'Andorra (Principality of)'),
  (214, 'es', 2, 'Europe/Madrid', 'es', 13, 'Spain'),
  (216, 'hu', 2, 'Europe/Budapest', 'hu', 13, 'Hungary (Republic of)'),
  (218, 'ba', 2, 'Europe/Sarajevo', '', 13, 'Bosnia and Herzegovina'),
  (219, 'hr', 2, 'Europe/Zagreb', 'hr', 13, 'Croatia (Republic of)'),
  (220, 'rs', 2, 'Europe/Belgrade', 'sr', 'Serbia (Republic of)'),
  (222, 'it', 2, 'Europe/Rome', 'it', 13, 'Italy'),
  (225, 'va', 2, 'Europe/Rome', 'it', 'Vatican City State'),
  (226, 'ro', 2, 'Europe/Bucharest', 'ro', 13, 'Romania'),
  (228, 'ch', 2, 'Europe/Zurich', 'de', 13, 'Switzerland (Confederation of)'),
  (230, 'cz', 2, 'Europe/Prague', 'cs', 'Czech Republic'),
  (231, 'sk', 2, 'Europe/Bratislava', 'sk', 13, 'Slovak Republic'),
  (232, 'at', 2, 'Europe/Vienna', 'de', 13, 'Austria'),
  (234, 'gb', 2, 'Europe/London', 'en', 13, 'United Kingdom of Great Britain and Northern Ireland'),
  (235, 'gb', 2, 'Europe/London', 'en', 13, 'United Kingdom of Great Britain and Northern Ireland'),
  (238, 'dk', 2, 'Europe/Copenhagen', 'da', 13, 'Denmark'),
  (240, 'se', 2, 'Europe/Stockholm', 'sv', 13, 'Sweden'),
  (242, 'no', 2, 'Europe/Oslo', 'no', 13, 'Norway'),
  (244, 'fi', 2, 'Europe/Helsinki', '', 13, 'Finland'),
  (246, 'lt', 2, 'Europe/Vilnius', 'lt', 13, 'Lithuania (Republic of)'),
  (247, 'lv', 2, 'Europe/Riga', 'lv', 13, 'Latvia (Republic of)'),
  (248, 'ee', 2, 'Europe/Tallinn', 'et', 13, 'Estonia (Republic of)'),
  (250, 'ru', 2, 'Europe/Moscow', '', 13, 'Russian Federation'),
  (255, 'ua', 2, 'Europe/Kiev', 'uk', 13, 'Ukraine'),
  (257, 'by', 2, 'Europe/Minsk', 'be', 13, 'Belarus (Republic of)'),
  (259, 'md', 2, 'Europe/Chisinau', 'Moldova (Republic of)'),
  (260, 'pl', 2, 'Europe/Warsaw', 'pl', 'Poland (Republic of)'),
  (262, 'de', 2, 'Europe/Berlin', 'de', 13, 'Germany (Federal Republic of)'),
  (266, 'gi', 2, 'Europe/Gibraltar', 'en', 'Gibraltar'),
  (268, 'pt', 2, 'Europe/Lisbon', 'pt', 13, 'Portugal'),
  (270, 'lu', 2, 'Europe/Luxembourg', '', 13, 'Luxembourg'),
  (272, 'ie', 2, 'Europe/Dublin', 'en', 13, 'Ireland'),
  (274, 'is', 2, 'Atlantic/Reykjavik', 'is', 13, 'Iceland'),
  (276, 'al', 2, 'Europe/Tirane', 'sq', 13, 'Albania (Republic of)'),
  (278, 'mt', 2, 'Europe/Malta', '', 13, 'Malta'),
  (280, 'cy', 2, 'Asia/Nicosia', '', 13, 'Cyprus (Republic of)'),
  (282, 'ge', 2, 'Asia/Tbilisi', '', 13, 'Georgia'),
  (283, 'am', 2, 'Asia/Yerevan', '', 13, 'Armenia (Republic of)'),
  (284, 'bg', 2, 'Europe/Sofia', 'bg', 13, 'Bulgaria (Republic of)'),
  (286, 'tr', 2, 'Europe/Istanbul', 'tr', 13, 'Turkey'),
  (288, 'fo', 2, 'Atlantic/Faroe', 'Faroe Islands'),
  (289, 'ge', 2, 'Asia/Tbilisi', 'ab', 13, 'Abkhazia (Georgia)'),
  (290, 'gl', 2, 'Arctic/Longyearbyen', 'kl', 13, 'Greenland (Denmark)'),
  (292, 'sm', 2, 'Europe/San_Marino', 'San Marino (Republic of)'),
  (293, 'si', 2, 'Europe/Ljubljana', 'sl', 13, 'Slovenia (Republic of)'),
  (294, 'mk', 2, 'Europe/Skopje', '', 13, 'The Former Yugoslav Republic of Macedonia'),
  (295, 'li', 2, 'Europe/Vaduz', 'de', 13, 'Liechtenstein (Principality of)'),
  (297, 'me', 2, 'Europe/Podgorica', 'Montenegro (Republic of)'),
  (302, 'ca', 3, '', '', 11, 'Canada'),
  (308, 'pm', 2, 'America/Miquelon', 'Saint Pierre and Miquelon (Collectivit territoriale de la Rpublique franaise)'),
  (310, 'us', 3, '', 'en', 11, 'United States of America'),
  (311, 'us', 3, '', 'en', 11, 'United States of America'),
  (312, 'us', 3, '', 'en', 11, 'United States of America'),
  (313, 'us', 3, '', 'en', 11, 'United States of America'),
  (314, 'us', 3, '', 'en', 11, 'United States of America'),
  (315, 'us', 3, '', 'en', 11, 'United States of America'),
  (316, 'us', 3, '', 'en', 11, 'United States of America'),
  (330, 'pr', 2, 'America/Puerto_Rico', 'Puerto Rico'),
  (332, 'vi', 2, 'America/St_Thomas', 'United States Virgin Islands'),
  (334, 'mx', 3, 'Mexico'),
  (338, 'jm', 3, 'America/Jamaica', '', 13, 'Jamaica'),
  (340, 'gp', 2, 'America/Guadeloupe', 'Guadeloupe (French Department of)'),
  (342, 'bb', 3, 'America/Barbados', '', 13, 'Barbados'),
  (344, 'ag', 3, 'America/Antigua', 'Antigua and Barbuda'),
  (346, 'ky', 3, 'America/Cayman', 'Cayman Islands'),
  (348, 'vg', 3, 'America/Tortola', 'British Virgin Islands'),
  (350, 'bm', 2, 'Atlantic/Bermuda', 'Bermuda'),
  (352, 'gd', 2, 'America/Grenada', 'en', 'Grenada'),
  (354, 'ms', 2, 'America/Montserrat', 'en', 'Montserrat'),
  (356, 'kn', 2, 'America/St_Kitts', 'Saint Kitts and Nevis'),
  (358, 'lc', 2, 'America/St_Lucia', 'Saint Lucia'),
  (360, 'vc', 2, 'America/St_Vincent', 'Saint Vincent and the Grenadines'),
  (362, 'nl', 2, '', 'nl', 13, 'Netherlands Antilles'),
  (363, 'aw', 2, 'America/Aruba', '', 13, 'Aruba'),
  (364, 'bs', 2, 'America/Nassau', 'en', 'Bahamas (Commonwealth of the)'),
  (365, 'ai', 3, 'America/Anguilla', 'en', 'Anguilla'),
  (366, 'dm', 2, 'America/Dominica', 'en', 'Dominica (Commonwealth of)'),
  (368, 'cu', 2, 'America/Havana', 'es', 'Cuba'),
  (370, 'do', 2, 'America/Santo_Domingo', 'Dominican Republic'),
  (372, 'ht', 2, 'America/Port-au-Prince', '', 13, 'Haiti (Republic of)'),
  (374, 'tt', 2, 'America/Port_of_Spain', '', 13, 'Trinidad and Tobago'),
  (376, 'tc', 2, 'America/Grand_Turk', 'Turks and Caicos Islands'),
  (400, 'az', 2, 'Asia/Baku', 'az', 13, 'Azerbaijani Republic'),
  (401, 'kz', 2, '', 'kk', 13, 'Kazakhstan (Republic of)'),
  (402, 'bt', 2, 'Asia/Thimphu', 'Bhutan (Kingdom of)'),
  (404, 'in', 2, 'Asia/Kolkata', '', 13, 'India (Republic of)'),
  (405, 'in', 2, 'Asia/Kolkata', '', 13, 'India (Republic of)'),
  (406, 'in', 2, 'Asia/Kolkata', '', 13, 'India (Republic of)'),
  (410, 'pk', 2, 'Asia/Karachi', '', 13, 'Pakistan (Islamic Republic of)'),
  (412, 'af', 2, 'Asia/Kabul', 'Afghanistan'),
  (413, 'lk', 2, 'Asia/Colombo', '', 13, 'Sri Lanka (Democratic Socialist Republic of)'),
  (414, 'mm', 2, 'Asia/Rangoon', 'my', 'Myanmar (Union of)'),
  (415, 'lb', 2, 'Asia/Beirut', '', 13, 'Lebanon'),
  (416, 'jo', 2, 'Asia/Amman', 'ar', 13, 'Jordan (Hashemite Kingdom of)'),
  (417, 'sy', 2, 'Asia/Damascus', 'ar', 13, 'Syrian Arab Republic'),
  (418, 'iq', 2, 'Asia/Baghdad', 'Iraq (Republic of)'),
  (419, 'kw', 2, 'Asia/Kuwait', 'ar', 13, 'Kuwait (State of)'),
  (420, 'sa', 2, 'Asia/Riyadh', 'ar', 13, 'Saudi Arabia (Kingdom of)'),
  (421, 'ye', 2, 'Asia/Aden', 'ar', 13, 'Yemen (Republic of)'),
  (422, 'om', 2, 'Asia/Muscat', '', 13, 'Oman (Sultanate of)'),
  (423, 'ps', 2, 'Asia/Gaza', 'Palestine'),
  (424, 'ae', 2, 'Asia/Dubai', '', 13, 'United Arab Emirates'),
  (425, 'il', 2, 'Asia/Tel_Aviv', '', 13, 'Israel (State of)'),
  (426, 'bh', 2, 'Asia/Bahrain', 'ar', 13, 'Bahrain (Kingdom of)'),
  (427, 'qa', 2, 'Asia/Qatar', '', 13, 'Qatar (State of)'),
  (428, 'mn', 2, '', 'mn', 'Mongolia'),
  (429, 'np', 2, 'Asia/Katmandu', '', 13, 'Nepal'),
  (430, 'ae', 2, 'Asia/Dubai', '', 13, 'United Arab Emirates'),
  (431, 'ae', 2, 'Asia/Dubai', '', 13, 'United Arab Emirates'),
  (432, 'ir', 2, 'Asia/Tehran', 'fa', 13, 'Iran (Islamic Republic of)'),
  (434, 'uz', 2, 'Asia/Tashkent', 'uz', 'Uzbekistan (Republic of)'),
  (436, 'tj', 2, 'Asia/Dushanbe', 'Tajikistan (Republic of)'),
  (437, 'kg', 2, 'Asia/Bishkek', 'Kyrgyz Republic'),
  (438, 'tm', 2, 'Asia/Ashgabat', 'tk', 'Turkmenistan'),
  (440, 'jp', 2, 'Asia/Tokyo', 'ja', 14, 'Japan'),
  (441, 'jp', 2, 'Asia/Tokyo', 'ja', 14, 'Japan'),
  (450, 'kr', 2, 'Asia/Seoul', 'ko', 13, 'Korea (Republic of)'),
  (452, 'vn', 2, 'Asia/Saigon', 'vi', 13, 'Viet Nam (Socialist Republic of)'),
  (454, 'hk', 2, 'Asia/Hong_Kong', '', 13, '"Hong Kong, China"'),
  (455, 'mo', 2, 'Asia/Macau', '', 13, '"Macao, China"'),
  (456, 'kh', 2, 'Asia/Phnom_Penh', 'km', 13, 'Cambodia (Kingdom of)'),
  (457, 'la', 2, 'Asia/Vientiane', 'lo', "Lao People's Democratic Republic"),
  (460, 'cn', 2, "Asia/Beijing", 'zh', 13, "China (People's Republic of)"),
  (461, 'cn', 2, "Asia/Beijing", 'zh', 13, "China (People's Republic of)"),
  (466, 'tw', 2, 'Asia/Taipei', "Taiwan (Republic of China)"),
  (467, 'kp', 2, 'Asia/Pyongyang', 'ko', 13, "Democratic People's Republic of Korea"),
  (470, 'bd', 2, 'Asia/Dhaka', '', 13, "Bangladesh (People's Republic of)"),
  (472, 'mv', 2, 'Indian/Maldives', 'dv', 'Maldives (Republic of)'),
  (502, 'my', 2, 'Asia/Kuala_Lumpur', '', 13, 'Malaysia'),
  (505, 'au', 2, 'Australia/Sydney', 'en', 13, 'Australia'),
  (510, 'id', 2, 'Asia/Jakarta', 'id', 13, 'Indonesia (Republic of)'),
  (514, 'tl', 2, 'Asia/Dili', 'Democratic Republic of Timor-Leste'),
  (515, 'ph', 2, '', 'tl', 13, 'Philippines (Republic of the)'),
  (520, 'th', 2, 'Asia/Bangkok', '', 13, 'Thailand'),
  (525, 'sg', 2, 'Asia/Singapore', 'en', 13, 'Singapore (Republic of)'),
  (528, 'bn', 2, 'Asia/Brunei', 'ms', 13, 'Brunei Darussalam'),
  (530, 'nz', 2, 'Pacific/Auckland', 'en', 13, 'New Zealand'),
  (534, 'mp', 2, 'Pacific/Saipan', 'Northern Mariana Islands (Commonwealth of the)'),
  (535, 'gu', 2, 'Pacific/Guam', 'en', 'Guam'),
  (536, 'nr', 2, 'Pacific/Nauru', 'Nauru (Republic of)'),
  (537, 'pg', 2, 'Pacific/Port_Moresby', '', 13, 'Papua New Guinea'),
  (539, 'to', 2, 'Pacific/Tongatapu', 'Tonga (Kingdom of)'),
  (540, 'sb', 2, 'Pacific/Guadalcanal', 'Solomon Islands'),
  (541, 'vu', 2, 'Pacific/Efate', 'Vanuatu (Republic of)'),
  (542, 'fj', 2, 'Pacific/Fiji', 'Fiji (Republic of)'),
  (543, 'wf', 2, 'Pacific/Wallis', "Wallis and Futuna (Territoire franais d'outre-mer)"),
  (544, 'as', 2, 'Pacific/Pago_Pago', 'American Samoa'),
  (545, 'ki', 2, 'Kiribati (Republic of)'),
  (546, 'nc', 2, 'Pacific/Noumea', "New Caledonia (Territoire franais d'outre-mer)"),
  (547, 'pf', 2, "French Polynesia (Territoire franais d'outre-mer)"),
  (548, 'ck', 2, 'Pacific/Rarotonga', 'Cook Islands'),
  (549, 'ws', 2, 'Pacific/Apia', 'Samoa (Independent State of)'),
  (550, 'fm', 2, 'Micronesia (Federated States of)'),
  (551, 'mh', 2, 'Marshall Islands (Republic of the)'),
  (552, 'pw', 2, 'Pacific/Palau', 'Palau (Republic of)'),
  (602, 'eg', 2, 'Africa/Cairo', '', 13, 'Egypt (Arab Republic of)'),
  (603, 'dz', 2, 'Africa/Algiers', '', 13, "Algeria (People's Democratic Republic of)"),
  (604, 'ma', 2, 'Africa/Casablanca', 'ar', 13, 'Morocco (Kingdom of)'),
  (605, 'tn', 2, 'Africa/Tunis', 'ar', 13, 'Tunisia'),
  (606, 'ly', 2, 'Africa/Tripoli', "Libya (Socialist People's Libyan Arab Jamahiriya)"),
  (607, 'gm', 2, 'Africa/Banjul', 'en', 'Gambia (Republic of the)'),
  (608, 'sn', 2, 'Africa/Dakar', 'Senegal (Republic of)'),
  (609, 'mr', 2, 'Africa/Nouakchott', 'ar', 'Mauritania (Islamic Republic of)'),
  (610, 'ml', 2, 'Africa/Bamako', 'fr', 'Mali (Republic of)'),
  (611, 'gn', 2, 'Africa/Conakry', 'fr', 'Guinea (Republic of)'),
  (612, 'ci', 2, 'Africa/Abidjan', "Cte d'Ivoire (Republic of)"),
  (613, 'bf', 2, 'Africa/Ouagadougou', 'fr', 'Burkina Faso'),
  (614, 'ne', 2, 'Africa/Niamey', 'Niger (Republic of the)'),
  (615, 'tg', 2, 'Africa/Lome', 'fr', 'Togolese Republic'),
  (616, 'bj', 2, 'Africa/Porto-Novo', 'fr', 'Benin (Republic of)'),
  (617, 'mu', 2, 'Indian/Mauritius', 'Mauritius (Republic of)'),
  (618, 'lr', 2, 'Africa/Monrovia', 'en', 'Liberia (Republic of)'),
  (619, 'sl', 2, 'Africa/Freetown', 'Sierra Leone'),
  (620, 'gh', 2, 'Africa/Accra', 'en', 'Ghana'),
  (621, 'ng', 2, 'Africa/Lagos', 'en', 'Nigeria (Federal Republic of)'),
  (622, 'td', 2, 'Africa/Ndjamena', 'Chad (Republic of)'),
  (623, 'cf', 2, 'Africa/Bangui', 'Central African Republic'),
  (624, 'cm', 2, 'Africa/Douala', 'fr', 'Cameroon (Republic of)'),
  (625, 'cv', 2, 'Atlantic/Cape_Verde', 'Cape Verde (Republic of)'),
  (626, 'st', 2, 'Africa/Sao_Tome', 'Sao Tome and Principe (Democratic Republic of)'),
  (627, 'gq', 2, 'Africa/Malabo', 'Equatorial Guinea (Republic of)'),
  (628, 'ga', 2, 'Africa/Libreville', 'fr', 'Gabonese Republic'),
  (629, 'cg', 2, 'Africa/Brazzaville', 'Congo (Republic of the)'),
  (630, 'cg', 2, 'Africa/Brazzaville', 'Democratic Republic of the Congo'),
  (631, 'ao', 2, 'Africa/Luanda', 'pt', 'Angola (Republic of)'),
  (632, 'gw', 2, 'Africa/Bissau', 'pt', 'Guinea-Bissau (Republic of)'),
  (633, 'sc', 2, 'Indian/Mahe', 'Seychelles (Republic of)'),
  (634, 'sd', 2, 'Africa/Khartoum', 'Sudan (Republic of the)'),
  (635, 'rw', 2, 'Africa/Kigali', 'Rwanda (Republic of)'),
  (636, 'et', 2, 'Africa/Addis_Ababa', 'Ethiopia (Federal Democratic Republic of)'),
  (637, 'so', 2, 'Africa/Mogadishu', 'Somali Democratic Republic'),
  (638, 'dj', 2, 'Africa/Djibouti', 'ar', 'Djibouti (Republic of)'),
  (639, 'ke', 2, 'Africa/Nairobi', '', 13, 'Kenya (Republic of)'),
  (640, 'tz', 2, 'Africa/Dar_es_Salaam', 'Tanzania (United Republic of)'),
  (641, 'ug', 2, 'Africa/Kampala', 'Uganda (Republic of)'),
  (642, 'bi', 2, 'Africa/Bujumbura', 'Burundi (Republic of)'),
  (643, 'mz', 2, 'Africa/Maputo', 'pt', 'Mozambique (Republic of)'),
  (645, 'zm', 2, 'Africa/Lusaka', 'Zambia (Republic of)'),
  (646, 'mg', 2, 'Indian/Antananarivo', 'Madagascar (Republic of)'),
  (647, 're', 2, 'Indian/Reunion', 'Reunion (French Department of)'),
  (648, 'zw', 2, 'Africa/Harare', '', 13, 'Zimbabwe (Republic of)'),
  (649, 'na', 2, 'Africa/Windhoek', 'en', 'Namibia (Republic of)'),
  (650, 'mw', 2, 'Africa/Blantyre', 'Malawi'),
  (651, 'ls', 2, 'Africa/Maseru', 'Lesotho (Kingdom of)'),
  (652, 'bw', 2, 'Africa/Gaborone', 'Botswana (Republic of)'),
  (653, 'sz', 2, 'Africa/Mbabane', 'Swaziland (Kingdom of)'),
  (654, 'km', 2, 'Indian/Comoro', 'Comoros (Union of the)'),
  (655, 'za', 2, 'Africa/Johannesburg', 'en', 13, 'South Africa (Republic of)'),
  (657, 'er', 2, 'Africa/Asmara', 'Eritrea'),
  (702, 'bz', 2, 'America/Belize', 'en', 13, 'Belize'),
  (704, 'gt', 2, 'America/Guatemala', 'Guatemala (Republic of)'),
  (706, 'sv', 2, 'America/El_Salvador', '', 13, 'El Salvador (Republic of)'),
  (708, 'hn', 3, 'America/Tegucigalpa', 'es', 13, 'Honduras (Republic of)'),
  (710, 'ni', 2, 'America/Managua', 'Nicaragua'),
  (712, 'cr', 2, 'America/Costa_Rica', '', 13, 'Costa Rica'),
  (714, 'pa', 2, 'America/Panama', 'es', 'Panama (Republic of)'),
  (716, 'pe', 2, 'America/Lima', 'es', 13, 'Peru'),
  (722, 'ar', 3, '', 'es', 13, 'Argentine Republic'),
  (724, 'br', 2, '', 'pt', 13, 'Brazil (Federative Republic of)'),
  (730, 'cl', 2, '', 'es', 13, 'Chile'),
  (732, 'co', 3, 'America/Bogota', 'Colombia (Republic of)'),
  (734, 've', 2, 'America/Caracas', 13, 'Venezuela (Bolivarian Republic of)'),
  (736, 'bo', 2, 'America/La_Paz', 13, 'Bolivia (Republic of)'),
  (738, 'gy', 2, 'America/Guyana', 'en', 'Guyana'),
  (740, 'ec', 2, '', 'es', 13, 'Ecuador'),
  (742, 'gf', 2, 'America/Cayenne', 'French Guiana (French Department of)'),
  (744, 'py', 2, 'America/Asuncion', 'Paraguay (Republic of)'),
  (746, 'sr', 2, 'America/Paramaribo', 'Suriname (Republic of)'),
  (748, 'uy', 2, 'America/Montevideo', 'es', 13, 'Uruguay (Eastern Republic of)'),
  (750, 'fk', 2, 'Atlantic/Stanley', 'Falkland Islands (Malvinas)')]

get_mcc = lambda elt: elt[0]
get_iso = lambda elt: elt[1]
get_sd = lambda elt: elt[2]
get_tz = lambda elt: len(elt) > 4 and elt[3] or ''
get_lang = lambda elt: len(elt) > 5 and elt[4] or ''
get_wifi = lambda elt: len(elt) > 6 and elt[5] or 0

mcc_codes = ['0x%04x' % get_mcc(elt) for elt in mcc_table]
tz_set = sorted(x for x in set(get_tz(elt) for elt in mcc_table))
lang_set = sorted(x for x in set(get_lang(elt) for elt in mcc_table))
iso_set = sorted(x for x in set(get_iso(elt) for elt in mcc_table))

def mk_ind_code(elt):
  iso_ind = iso_set.index(get_iso(elt)) & 0x01FF         #  9 bits
  wifi = get_wifi(elt) & 0x000F                          #  4 bits
  sd = get_sd(elt) & 0x0003                              #  2 bits
  tz_ind = tz_set.index(get_tz(elt)) & 0x03FF            #  9 bits
  lang_ind = lang_set.index(get_lang(elt)) & 0x00FF      #  8 bits
  return (iso_ind << 23) | (wifi << 19) | (sd << 17) | (tz_ind << 8) | lang_ind

ind_codes = ['0x%08x' % mk_ind_code(elt) for elt in mcc_table]

def fmt_list(title, l, batch_sz):
  sl = []
  for i in range(len(l) / batch_sz + (len(l) % batch_sz and 1 or 0)):
    j = i * batch_sz
    sl.append((' ' * 8) + ', '.join(l[j:j + batch_sz]))
  return '    private static final %s = {\n' % title + ',\n'.join(sl) + '\n    };\n'

def do_autogen_comment(extra_desc=[]):
  print '    /' + '**\n    * AUTO GENERATED (by the Python code above)'
  for line in extra_desc:
    print '    * %s' % line
  print '    *' + '/'

do_autogen_comment()
print fmt_list('String[] ISO_STRINGS', ['"%s"' % x for x in iso_set], 10)
do_autogen_comment()
print fmt_list('String[] TZ_STRINGS', ['"%s"' % x for x in tz_set], 1)
do_autogen_comment()
print fmt_list('String[] LANG_STRINGS', ['"%s"' % x for x in lang_set], 10)
do_autogen_comment(['This table is a list of MCC codes.  The index in this table',
                    'of a given MCC code is the index of extra information about',
                    'that MCC in the IND_CODES table.'])
print fmt_list('short[] MCC_CODES', mcc_codes, 10)
do_autogen_comment(['The values in this table are broken down as follows (msb to lsb):',
                    '    iso country code  9 bits',
                    '    wifi channel      4 bits',
                    '    smalled digit     2 bits',
                    '    default timezone  9 bits',
                    '    default language  8 bits'])
print fmt_list('int[] IND_CODES', ind_codes, 6)

def parse_ind_code(ind):
  mcc = eval(mcc_codes[ind])
  code = eval(ind_codes[ind])
  iso_ind = int((code >> 23) & 0x01FF)
  wifi = int((code >> 19) & 0x000F)
  sd = int((code >> 17) & 0x0003)
  tz_ind = int((code >> 8) & 0x01FF)
  lang_ind = (code >> 0) & 0x00FF
  return (mcc, iso_set[iso_ind], sd, tz_set[tz_ind], lang_set[lang_ind], wifi)

fmt_str = 'mcc = %s, iso = %s, sd = %s, tz = %s, lang = %s, wifi = %s'
orig_table = [fmt_str % (get_mcc(elt), get_iso(elt), get_sd(elt),
                         get_tz(elt), get_lang(elt), get_wifi(elt))
              for elt in mcc_table]
derived_table = [fmt_str % parse_ind_code(i) for i in range(len(ind_codes))]
for i in range(len(orig_table)):
  if orig_table[i] == derived_table[i]: continue
  print 'MISMATCH ERROR : ', orig_table[i], " != ", derived_table[i]

*/

/**
 * Mobile Country Code
 *
 * {@hide}
 */
public final class MccTable
{
    /**
    * AUTO GENERATED (by the Python code above)
    */
    private static final String[] ISO_STRINGS = {
        "ad", "ae", "af", "ag", "ai", "al", "am", "ao", "ar", "as",
        "at", "au", "aw", "az", "ba", "bb", "bd", "be", "bf", "bg",
        "bh", "bi", "bj", "bm", "bn", "bo", "br", "bs", "bt", "bw",
        "by", "bz", "ca", "cf", "cg", "ch", "ci", "ck", "cl", "cm",
        "cn", "co", "cr", "cu", "cv", "cy", "cz", "de", "dj", "dk",
        "dm", "do", "dz", "ec", "ee", "eg", "er", "es", "et", "fi",
        "fj", "fk", "fm", "fo", "fr", "ga", "gb", "gd", "ge", "gf",
        "gh", "gi", "gl", "gm", "gn", "gp", "gq", "gr", "gt", "gu",
        "gw", "gy", "hk", "hn", "hr", "ht", "hu", "id", "ie", "il",
        "in", "iq", "ir", "is", "it", "jm", "jo", "jp", "ke", "kg",
        "kh", "ki", "km", "kn", "kp", "kr", "kw", "ky", "kz", "la",
        "lb", "lc", "li", "lk", "lr", "ls", "lt", "lu", "lv", "ly",
        "ma", "mc", "md", "me", "mg", "mh", "mk", "ml", "mm", "mn",
        "mo", "mp", "mr", "ms", "mt", "mu", "mv", "mw", "mx", "my",
        "mz", "na", "nc", "ne", "ng", "ni", "nl", "no", "np", "nr",
        "nz", "om", "pa", "pe", "pf", "pg", "ph", "pk", "pl", "pm",
        "pr", "ps", "pt", "pw", "py", "qa", "re", "ro", "rs", "ru",
        "rw", "sa", "sb", "sc", "sd", "se", "sg", "si", "sk", "sl",
        "sm", "sn", "so", "sr", "st", "sv", "sy", "sz", "tc", "td",
        "tg", "th", "tj", "tl", "tm", "tn", "to", "tr", "tt", "tw",
        "tz", "ua", "ug", "us", "uy", "uz", "va", "vc", "ve", "vg",
        "vi", "vn", "vu", "wf", "ws", "ye", "za", "zm", "zw"
    };

    /**
    * AUTO GENERATED (by the Python code above)
    */
    private static final String[] TZ_STRINGS = {
        "",
        "Africa/Abidjan",
        "Africa/Accra",
        "Africa/Addis_Ababa",
        "Africa/Algiers",
        "Africa/Asmara",
        "Africa/Bamako",
        "Africa/Bangui",
        "Africa/Banjul",
        "Africa/Bissau",
        "Africa/Blantyre",
        "Africa/Brazzaville",
        "Africa/Bujumbura",
        "Africa/Cairo",
        "Africa/Casablanca",
        "Africa/Conakry",
        "Africa/Dakar",
        "Africa/Dar_es_Salaam",
        "Africa/Djibouti",
        "Africa/Douala",
        "Africa/Freetown",
        "Africa/Gaborone",
        "Africa/Harare",
        "Africa/Johannesburg",
        "Africa/Kampala",
        "Africa/Khartoum",
        "Africa/Kigali",
        "Africa/Lagos",
        "Africa/Libreville",
        "Africa/Lome",
        "Africa/Luanda",
        "Africa/Lusaka",
        "Africa/Malabo",
        "Africa/Maputo",
        "Africa/Maseru",
        "Africa/Mbabane",
        "Africa/Mogadishu",
        "Africa/Monrovia",
        "Africa/Nairobi",
        "Africa/Ndjamena",
        "Africa/Niamey",
        "Africa/Nouakchott",
        "Africa/Ouagadougou",
        "Africa/Porto-Novo",
        "Africa/Sao_Tome",
        "Africa/Tripoli",
        "Africa/Tunis",
        "Africa/Windhoek",
        "America/Anguilla",
        "America/Antigua",
        "America/Aruba",
        "America/Asuncion",
        "America/Barbados",
        "America/Belize",
        "America/Bogota",
        "America/Caracas",
        "America/Cayenne",
        "America/Cayman",
        "America/Costa_Rica",
        "America/Dominica",
        "America/El_Salvador",
        "America/Grand_Turk",
        "America/Grenada",
        "America/Guadeloupe",
        "America/Guatemala",
        "America/Guyana",
        "America/Havana",
        "America/Jamaica",
        "America/La_Paz",
        "America/Lima",
        "America/Managua",
        "America/Miquelon",
        "America/Montevideo",
        "America/Montserrat",
        "America/Nassau",
        "America/Panama",
        "America/Paramaribo",
        "America/Port-au-Prince",
        "America/Port_of_Spain",
        "America/Puerto_Rico",
        "America/Santo_Domingo",
        "America/St_Kitts",
        "America/St_Lucia",
        "America/St_Thomas",
        "America/St_Vincent",
        "America/Tegucigalpa",
        "America/Tortola",
        "Arctic/Longyearbyen",
        "Asia/Aden",
        "Asia/Amman",
        "Asia/Ashgabat",
        "Asia/Baghdad",
        "Asia/Bahrain",
        "Asia/Baku",
        "Asia/Bangkok",
        "Asia/Beijing",
        "Asia/Beirut",
        "Asia/Bishkek",
        "Asia/Brunei",
        "Asia/Colombo",
        "Asia/Damascus",
        "Asia/Dhaka",
        "Asia/Dili",
        "Asia/Dubai",
        "Asia/Dushanbe",
        "Asia/Gaza",
        "Asia/Hong_Kong",
        "Asia/Jakarta",
        "Asia/Kabul",
        "Asia/Karachi",
        "Asia/Katmandu",
        "Asia/Kolkata",
        "Asia/Kuala_Lumpur",
        "Asia/Kuwait",
        "Asia/Macau",
        "Asia/Muscat",
        "Asia/Nicosia",
        "Asia/Phnom_Penh",
        "Asia/Pyongyang",
        "Asia/Qatar",
        "Asia/Rangoon",
        "Asia/Riyadh",
        "Asia/Saigon",
        "Asia/Seoul",
        "Asia/Singapore",
        "Asia/Taipei",
        "Asia/Tashkent",
        "Asia/Tbilisi",
        "Asia/Tehran",
        "Asia/Tel_Aviv",
        "Asia/Thimphu",
        "Asia/Tokyo",
        "Asia/Vientiane",
        "Asia/Yerevan",
        "Atlantic/Bermuda",
        "Atlantic/Cape_Verde",
        "Atlantic/Faroe",
        "Atlantic/Reykjavik",
        "Atlantic/Stanley",
        "Australia/Sydney",
        "Europe/Amsterdam",
        "Europe/Andorra",
        "Europe/Athens",
        "Europe/Belgrade",
        "Europe/Berlin",
        "Europe/Bratislava",
        "Europe/Brussels",
        "Europe/Bucharest",
        "Europe/Budapest",
        "Europe/Chisinau",
        "Europe/Copenhagen",
        "Europe/Dublin",
        "Europe/Gibraltar",
        "Europe/Helsinki",
        "Europe/Istanbul",
        "Europe/Kiev",
        "Europe/Lisbon",
        "Europe/Ljubljana",
        "Europe/London",
        "Europe/Luxembourg",
        "Europe/Madrid",
        "Europe/Malta",
        "Europe/Minsk",
        "Europe/Monaco",
        "Europe/Moscow",
        "Europe/Oslo",
        "Europe/Paris",
        "Europe/Podgorica",
        "Europe/Prague",
        "Europe/Riga",
        "Europe/Rome",
        "Europe/San_Marino",
        "Europe/Sarajevo",
        "Europe/Skopje",
        "Europe/Sofia",
        "Europe/Stockholm",
        "Europe/Tallinn",
        "Europe/Tirane",
        "Europe/Vaduz",
        "Europe/Vienna",
        "Europe/Vilnius",
        "Europe/Warsaw",
        "Europe/Zagreb",
        "Europe/Zurich",
        "Indian/Antananarivo",
        "Indian/Comoro",
        "Indian/Mahe",
        "Indian/Maldives",
        "Indian/Mauritius",
        "Indian/Reunion",
        "Pacific/Apia",
        "Pacific/Auckland",
        "Pacific/Efate",
        "Pacific/Fiji",
        "Pacific/Guadalcanal",
        "Pacific/Guam",
        "Pacific/Nauru",
        "Pacific/Noumea",
        "Pacific/Pago_Pago",
        "Pacific/Palau",
        "Pacific/Port_Moresby",
        "Pacific/Rarotonga",
        "Pacific/Saipan",
        "Pacific/Tongatapu",
        "Pacific/Wallis"
    };

    /**
    * AUTO GENERATED (by the Python code above)
    */
    private static final String[] LANG_STRINGS = {
        "13", "", "ab", "ar", "az", "be", "bg", "ca", "cs", "da",
        "de", "dv", "el", "en", "es", "et", "fa", "fr", "hr", "hu",
        "id", "is", "it", "ja", "kk", "kl", "km", "ko", "lo", "lt",
        "lv", "mn", "ms", "my", "nl", "no", "pl", "pt", "ro", "sk",
        "sl", "sq", "sr", "sv", "tk", "tl", "tr", "uk", "uz", "vi",
        "zh"
    };

    /**
    * AUTO GENERATED (by the Python code above)
    * This table is a list of MCC codes.  The index in this table
    * of a given MCC code is the index of extra information about
    * that MCC in the IND_CODES table.
    */
    private static final short[] MCC_CODES = {
        0x00ca, 0x00cc, 0x00ce, 0x00d0, 0x00d4, 0x00d5, 0x00d6, 0x00d8, 0x00da, 0x00db,
        0x00dc, 0x00de, 0x00e1, 0x00e2, 0x00e4, 0x00e6, 0x00e7, 0x00e8, 0x00ea, 0x00eb,
        0x00ee, 0x00f0, 0x00f2, 0x00f4, 0x00f6, 0x00f7, 0x00f8, 0x00fa, 0x00ff, 0x0101,
        0x0103, 0x0104, 0x0106, 0x010a, 0x010c, 0x010e, 0x0110, 0x0112, 0x0114, 0x0116,
        0x0118, 0x011a, 0x011b, 0x011c, 0x011e, 0x0120, 0x0121, 0x0122, 0x0124, 0x0125,
        0x0126, 0x0127, 0x0129, 0x012e, 0x0134, 0x0136, 0x0137, 0x0138, 0x0139, 0x013a,
        0x013b, 0x013c, 0x014a, 0x014c, 0x014e, 0x0152, 0x0154, 0x0156, 0x0158, 0x015a,
        0x015c, 0x015e, 0x0160, 0x0162, 0x0164, 0x0166, 0x0168, 0x016a, 0x016b, 0x016c,
        0x016d, 0x016e, 0x0170, 0x0172, 0x0174, 0x0176, 0x0178, 0x0190, 0x0191, 0x0192,
        0x0194, 0x0195, 0x0196, 0x019a, 0x019c, 0x019d, 0x019e, 0x019f, 0x01a0, 0x01a1,
        0x01a2, 0x01a3, 0x01a4, 0x01a5, 0x01a6, 0x01a7, 0x01a8, 0x01a9, 0x01aa, 0x01ab,
        0x01ac, 0x01ad, 0x01ae, 0x01af, 0x01b0, 0x01b2, 0x01b4, 0x01b5, 0x01b6, 0x01b8,
        0x01b9, 0x01c2, 0x01c4, 0x01c6, 0x01c7, 0x01c8, 0x01c9, 0x01cc, 0x01cd, 0x01d2,
        0x01d3, 0x01d6, 0x01d8, 0x01f6, 0x01f9, 0x01fe, 0x0202, 0x0203, 0x0208, 0x020d,
        0x0210, 0x0212, 0x0216, 0x0217, 0x0218, 0x0219, 0x021b, 0x021c, 0x021d, 0x021e,
        0x021f, 0x0220, 0x0221, 0x0222, 0x0223, 0x0224, 0x0225, 0x0226, 0x0227, 0x0228,
        0x025a, 0x025b, 0x025c, 0x025d, 0x025e, 0x025f, 0x0260, 0x0261, 0x0262, 0x0263,
        0x0264, 0x0265, 0x0266, 0x0267, 0x0268, 0x0269, 0x026a, 0x026b, 0x026c, 0x026d,
        0x026e, 0x026f, 0x0270, 0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0276, 0x0277,
        0x0278, 0x0279, 0x027a, 0x027b, 0x027c, 0x027d, 0x027e, 0x027f, 0x0280, 0x0281,
        0x0282, 0x0283, 0x0285, 0x0286, 0x0287, 0x0288, 0x0289, 0x028a, 0x028b, 0x028c,
        0x028d, 0x028e, 0x028f, 0x0291, 0x02be, 0x02c0, 0x02c2, 0x02c4, 0x02c6, 0x02c8,
        0x02ca, 0x02cc, 0x02d2, 0x02d4, 0x02da, 0x02dc, 0x02de, 0x02e0, 0x02e2, 0x02e4,
        0x02e6, 0x02e8, 0x02ea, 0x02ec, 0x02ee
    };

    /**
    * AUTO GENERATED (by the Python code above)
    * The values in this table are broken down as follows (msb to lsb):
    *     iso country code  9 bits
    *     wifi channel      4 bits
    *     smalled digit     2 bits
    *     default timezone  9 bits
    *     default language  8 bits
    */
    private static final int[] IND_CODES = {
        0x26ec8e0c, 0x496c8c22, 0x08ec9201, 0x206ca611, 0x3ceca311, 0x00048d07,
        0x1ceca00e, 0x2b6c9413, 0x076cac01, 0x2a6cb612, 0x54048f2a, 0x2f6caa16,
        0x6704aa16, 0x53ec9326, 0x11ecb70a, 0x1704a808, 0x596c9127, 0x056cb30a,
        0x216c9e0d, 0x216c9e0d, 0x18ec9609, 0x57ecaf2b, 0x49eca523, 0x1dec9901,
        0x3a6cb41d, 0x3b6ca91e, 0x1b6cb00f, 0x54eca401, 0x64ec9b2f, 0x0f6ca205,
        0x3d049501, 0x4f04b524, 0x17ec900a, 0x2384980d, 0x516c9c25, 0x3aec9f01,
        0x2c6c970d, 0x2eec8915, 0x02ecb129, 0x436ca101, 0x16ec7401, 0x226c7f01,
        0x036c8501, 0x09ecae06, 0x62ec9a2e, 0x1f848801, 0x226c7f02, 0x246c5719,
        0x5a04ab01, 0x58ec9d28, 0x3f6cad01, 0x386cb20a, 0x3d84a701, 0x105e0001,
        0x4f844701, 0x65de000d, 0x65de000d, 0x65de000d, 0x65de000d, 0x65de000d,
        0x65de000d, 0x65de000d, 0x50044f01, 0x69045301, 0x45060001, 0x2fee4301,
        0x25843f01, 0x07ee3401, 0x01863101, 0x35863901, 0x68865601, 0x0b848601,
        0x21843e0d, 0x4284490d, 0x33845101, 0x37845201, 0x67845401, 0x496c0022,
        0x066c3201, 0x0d844a0d, 0x0206300d, 0x19043b0d, 0x1584420e, 0x19845001,
        0x2aec4d01, 0x636c4e01, 0x5e043d01, 0x06ec5d04, 0x366c0018, 0x0e048201,
        0x2d6c6f01, 0x2d6c6f01, 0x2d6c6f01, 0x4eec6d01, 0x01046c01, 0x38ec6301,
        0x40047821, 0x376c6001, 0x306c5903, 0x5d6c6403, 0x2d845b01, 0x356c7103,
        0x55ec7903, 0x6bec5803, 0x4bec7301, 0x50846901, 0x00ec6701, 0x2cec8101,
        0x0a6c5c03, 0x52ec7701, 0x4084001f, 0x4a6c6e01, 0x00ec6701, 0x00ec6701,
        0x2e6c8010, 0x66847e30, 0x60046801, 0x31846101, 0x61045a2c, 0x30f48317,
        0x30f48317, 0x34ec7b1b, 0x69ec7a31, 0x296c6a01, 0x416c7201, 0x326c751a,
        0x3684841c, 0x146c5f32, 0x146c5f32, 0x63847d01, 0x346c761b, 0x086c6501,
        0x4404bb0b, 0x45ec7001, 0x05ec8b0d, 0x2bec6b14, 0x60846601, 0x4e6c002d,
        0x5fec5e01, 0x586c7c0d, 0x0c6c6220, 0x4b6cbf0d, 0x4184ca01, 0x2784c30d,
        0x4a84c401, 0x4decc801, 0x6204cb01, 0x5604c201, 0x6a04c001, 0x1e04c101,
        0x6a84cc01, 0x0484c601, 0x32840001, 0x4704c501, 0x4d040001, 0x1284c901,
        0x6b04be01, 0x1f040001, 0x3e840001, 0x5184c701, 0x1bec0d01, 0x1a6c0401,
        0x3c6c0e03, 0x61ec2e03, 0x3b842d01, 0x2484080d, 0x5a841001, 0x42042903,
        0x3f840611, 0x25040f11, 0x12040101, 0x09042a11, 0x47842801, 0x5f041d11,
        0x0b042b11, 0x4384bc01, 0x3904250d, 0x59841401, 0x2304020d, 0x48041b0d,
        0x5e842701, 0x10840701, 0x13841311, 0x16048701, 0x5c042c01, 0x26042001,
        0x20841c11, 0x11040b01, 0x11040b01, 0x03841e25, 0x28040925, 0x5684ba01,
        0x57041901, 0x55041a01, 0x1d040301, 0x5b042401, 0x18041203, 0x316c2601,
        0x64041101, 0x65041801, 0x0a840c01, 0x46042125, 0x6c841f01, 0x3e04b801,
        0x5304bd01, 0x6d6c1601, 0x46842f0d, 0x44840a01, 0x39842201, 0x0e841501,
        0x5d842301, 0x3304b901, 0x6c6c170d, 0x1c040501, 0x0fec350d, 0x27044001,
        0x5cec3c01, 0x29ee550e, 0x48844601, 0x156c3a01, 0x4c044b0e, 0x4cec450e,
        0x046e000e, 0x0d6c0025, 0x136c000e, 0x14863601, 0x68043700, 0x0c844400,
        0x2884410d, 0x1aec000e, 0x22843801, 0x52043301, 0x5b844c01, 0x666c480e,
        0x1e848a01
    };

    static final String LOG_TAG = "MccTable";

    /**
     * Given a Mobile Country Code, returns a default time zone ID
     * if available.  Returns null if unavailable.
     */
    public static String defaultTimeZoneForMcc(int mcc) {
        int index = Arrays.binarySearch(MCC_CODES, (short)mcc);
        if (index < 0) {
            return null;
        }
        int indCode = IND_CODES[index];
        int tzInd = (indCode >>> 4) & 0x001F;
        String tz = TZ_STRINGS[tzInd];
        if (tz == "") {
            return null;
        }
        return tz;
    }

    /**
     * Given a Mobile Country Code, returns an ISO two-character
     * country code if available.  Returns "" if unavailable.
     */
    public static String countryCodeForMcc(int mcc) {
        int index = Arrays.binarySearch(MCC_CODES, (short)mcc);
        if (index < 0) {
            return "";
        }
        int indCode = IND_CODES[index];
        int isoInd = (indCode >>> 23) & 0x01FF;
        return ISO_STRINGS[isoInd];
    }

    /**
     * Given a GSM Mobile Country Code, returns an ISO 2-3 character
     * language code if available.  Returns null if unavailable.
     */
    public static String defaultLanguageForMcc(int mcc) {
        int index = Arrays.binarySearch(MCC_CODES, (short)mcc);
        if (index < 0) {
            return null;
        }
        int indCode = IND_CODES[index];
        int langInd = indCode & 0x000F;
        String lang = LANG_STRINGS[langInd];
        if (lang == "") {
            return null;
        }
        return lang;
    }

    /**
     * Given a GSM Mobile Country Code, returns the corresponding
     * smallest number of digits field.  Returns 2 if unavailable.
     */
    public static int smallestDigitsMccForMnc(int mcc) {
        int index = Arrays.binarySearch(MCC_CODES, (short)mcc);
        if (index < 0) {
            return 2;
        }
        int indCode = IND_CODES[index];
        int smDig = (indCode >>> 9) & 0x0003;
        return smDig;
    }

    /**
     * Given a GSM Mobile Country Code, returns the number of wifi
     * channels allowed in that country.  Returns 0 if unavailable.
     */
    public static int wifiChannelsForMcc(int mcc) {
        int index = Arrays.binarySearch(MCC_CODES, (short)mcc);
        if (index < 0) {
            return 0;
        }
        int indCode = IND_CODES[index];
        int wifi = (indCode >>> 11) & 0x000F;
        return wifi;
    }

    /**
     * Updates MCC and MNC device configuration information for application retrieving
     * correct version of resources.  If either MCC or MNC is 0, they will be ignored (not set).
     * @param phone PhoneBae to act on.
     * @param mccmnc truncated imsi with just the MCC and MNC - MNC assumed to be from 4th to end
     */
    public static void updateMccMncConfiguration(PhoneBase phone, String mccmnc) {
        if (!TextUtils.isEmpty(mccmnc)) {
            int mcc, mnc;

            try {
                mcc = Integer.parseInt(mccmnc.substring(0,3));
                mnc = Integer.parseInt(mccmnc.substring(3));
            } catch (NumberFormatException e) {
                Log.e(LOG_TAG, "Error parsing IMSI");
                return;
            }

            Log.d(LOG_TAG, "updateMccMncConfiguration: mcc=" + mcc + ", mnc=" + mnc);

            if (mcc != 0) {
                setTimezoneFromMccIfNeeded(phone, mcc);
                setLocaleFromMccIfNeeded(phone, mcc);
                setWifiChannelsFromMcc(phone, mcc);
            }
            try {
                Configuration config = ActivityManagerNative.getDefault().getConfiguration();
                if (mcc != 0) {
                    config.mcc = mcc;
                }
                if (mnc != 0) {
                    config.mnc = mnc;
                }
                ActivityManagerNative.getDefault().updateConfiguration(config);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Can't update configuration", e);
            }
        }
    }

    /**
     * If the timezone is not already set, set it based on the MCC of the SIM.
     * @param phone PhoneBase to act on (get context from).
     * @param mcc Mobile Country Code of the SIM or SIM-like entity (build prop on CDMA)
     */
    private static void setTimezoneFromMccIfNeeded(PhoneBase phone, int mcc) {
        String timezone = SystemProperties.get(ServiceStateTracker.TIMEZONE_PROPERTY);
        if (timezone == null || timezone.length() == 0) {
            String zoneId = defaultTimeZoneForMcc(mcc);
            if (zoneId != null && zoneId.length() > 0) {
                Context context = phone.getContext();
                // Set time zone based on MCC
                AlarmManager alarm =
                        (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarm.setTimeZone(zoneId);
                Log.d(LOG_TAG, "timezone set to "+zoneId);
            }
        }
    }

    /**
     * If the locale is not already set, set it based on the MCC of the SIM.
     * @param phone PhoneBase to act on.
     * @param mcc Mobile Country Code of the SIM or SIM-like entity (build prop on CDMA)
     */
    private static void setLocaleFromMccIfNeeded(PhoneBase phone, int mcc) {
        String language = MccTable.defaultLanguageForMcc(mcc);
        String country = MccTable.countryCodeForMcc(mcc);

        Log.d(LOG_TAG, "locale set to "+language+"_"+country);
        phone.setSystemLocale(language, country);
    }

    /**
     * If the number of allowed wifi channels has not been set, set it based on
     * the MCC of the SIM.
     * @param phone PhoneBase to act on (get context from).
     * @param mcc Mobile Country Code of the SIM or SIM-like entity (build prop on CDMA)
     */
    private static void setWifiChannelsFromMcc(PhoneBase phone, int mcc) {
        int wifiChannels = MccTable.wifiChannelsForMcc(mcc);
        if (wifiChannels != 0) {
            Context context = phone.getContext();
            Log.d(LOG_TAG, "WIFI_NUM_ALLOWED_CHANNELS set to " + wifiChannels);
            WifiManager wM = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            //persist
            wM.setNumAllowedChannels(wifiChannels, true);
        }
    }
}
