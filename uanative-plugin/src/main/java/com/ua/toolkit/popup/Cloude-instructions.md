// TODO
// CREATE THE DEFAULT CONFIG SETUP FOR THE GOOD SETUP
// CREATE 2/3 VERSIONS OF THE AD CONFIG

Original Background Color: #80000000 (50% Transparent Black)

Original Button Color: #4CAF50 (Solid Green)

//  stage 1 popup being a circle instead  -     "get_button": {
"text": "GET",
"button_color": "#00C853",
"width_dp": 80,
"height_dp": 80,
"text_size_sp": 20,
"corner_radius_dp": 100,
"text_color": "#FFFFFF"
},
"popup_card": {
"background_color": "#1A1A2E",
"corner_radius_dp": 100
}50

{
"cross_promo": {
"enabled": true,
"disable_if_no_ads_purchased": false,
"show_close_button_after_time_in_sec": 5,
"allow_close_outside_screen": false,
"max_impressions_per_session": 10,
"visuals": {
"title": "CLOUD GAME",
"description": "Try this out!!",
"button_text": "GET"
}
},
"interstitial": {
"enabled": true,
"ios_store_id": "6755595667",
"show_close_button_after_time_in_sec": 10,
"show_install_popup_after_time_in_sec": 3,
"show_skip_button_after_time_in_sec": 5,
"start_pulse_after_time_in_sec": 5,
"visuals": {
"reward_texts": {
"reward_countdown_text": "Reward in: %ds",
"reward_earned_text": "Reward earned!",
"reward_text_size_sp": 14,
"reward_text_color": "#FFFFFF",
"open_store_button_text": "Open Store"
},
"get_button": {
"text": "GET",
"button_color": "#473FFF",
"width_dp": 145,
"height_dp": 55,
"text_size_sp": 20,
"corner_radius_dp": 100,
"text_color": "#FFFFFF"
},
"popup_card": {
"background_color": "#80FF0000",
"corner_radius_dp": 100
}
}
},
"placement": "native",
"devices": {
"1EFAFF48-5829-4F28-83B9-50A451DFB58C": {},
"2E79B74A-BDA9-4492-91F1-E9DD2F4861E7": {},
"4AF74462-6B16-47F0-899D-A76E3A76F685": {},
"73298AC0-3A24-4F21-85AE-0C8BE5676C22": {},
"B4EF4C4C-06B2-46F1-AE9B-B36846B7F355": {},
"B80DD70A-1A42-4792-BA37-E66C641EF364": {},
"BEF8B1A3-4880-4FFB-97E3-12F2FC7FBEB6": {},
"CB3B2E28-C528-4CF4-BE79-6BC85F2D92D8": {},
"D8A074E3-C4C1-4C18-AB12-3EF8CCB64FA5": {},
"E3F4126B-7F21-492A-A525-4D1FC2AC790B": {},
"cf13ca4db441c984736952dcafc0993e": {},
"dcf6a2c1-68e0-4c4a-9d65-10ca1ad1abe1": {},
"e1c9e05f9d073ba8f72d1c27f890ada1": {},
"f1f99dd1a9c89cfd8bccf00a56713e42": {
"device_id": "f1f99dd1a9c89cfd8bccf00a56713e42",
"games": [
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.IAmSecurityAirport"
},
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.IAmBird"
},
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.IAmMonkey"
},
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.IAmCat"
},
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.TaxiDriver"
},
{
"platform": "Android",
"bundle_id": "com.NewFolderGames.IAmSecurirty"
}
]
}
}
}