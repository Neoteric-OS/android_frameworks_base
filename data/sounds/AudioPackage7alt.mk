#
# Audio Package 7 - Tuna - Alternate names
#
# Include this file in a product makefile to include these audio files
#
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Argon Carbon Helium Krypton Neon Oxygen
NOTIFICATION_FILES := Adara Arcturus Bellatrix Capella CetiAlpha Hojus Lalande Mira Polaris Pollux Procyon Proxima Shaula Spica Tejat Upsilon Vega
RINGTONE_FILES := Andromeda Aquila ArgoNavis CanisMajor Carina Centaurus Cygnus Draco Girtab Hydra Machina Orion Pegasus Perseus Pyxis Rigel Scarabaeus Sceptrum Solarium Themos UrsaMinor Zeta
EFFECT_FILES := camera_focus Dock Effect_Tick KeypressStandard KeypressSpacebar KeypressDelete KeypressInvalid \
	KeypressReturn Lock Trusted Undock Unlock 
MATERIAL_EFFECT_FILES := camera_click LowBattery VideoRecord VideoStop

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
