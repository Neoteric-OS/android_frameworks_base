#
# Audio Package 10 - Mako
#
# Include this file in a product makefile to include these audio files
#
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Argon Carbon Helium Krypton Neon Oxygen Osmium Platinum
NOTIFICATION_FILES := Adara Alya Arcturus Capella CetiAlpha Hojus Mira Pollux Procyon Shaula Spica Syrma Talitha Tejat Vega
RINGTONE_FILES := Andromeda Aquila Atria ArgoNavis Centaurus Girtab Hydra Kuma Machina Orion Pegasus Pyxis Rasalas Scarabaeus \
	Sceptrum Solarium Themos Zeta
EFFECT_FILES := camera_focus ChargingStarted Dock Effect_Tick_48k InCallNotification KeypressStandard_48k KeypressSpacebar_48k \
	KeypressDelete_48k KeypressInvalid_48k KeypressReturn_48k Lock_48k Trusted_48k Undock Unlock_48k 
MATERIAL_EFFECT_FILES := camera_click_48k LowBattery_48k VideoRecord_48k VideoStop_48k WirelessChargingStarted_48k

PRODUCT_COPY_FILES += $(foreach fn,$(ALARM_FILES),\
	$(LOCAL_PATH)/alarms/ogg-jp/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(NOTIFICATION_FILES),\
	$(LOCAL_PATH)/notifications/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(RINGTONE_FILES),\
	$(LOCAL_PATH)/ringtones/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(EFFECT_FILES),\
	$(LOCAL_PATH)/effects/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)
PRODUCT_COPY_FILES += $(foreach fn,$(MATERIAL_EFFECT_FILES),\
	$(LOCAL_PATH)/effects/material/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)
