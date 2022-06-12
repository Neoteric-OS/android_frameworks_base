#
# Audio Package 6 - Trygon/Stingray
#
# Include this file in a product makefile to include these audio files
#
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Barium Cesium Plutonium Scandium
NOTIFICATION_FILES := Antimony Argon Beryllium Cobalt Fluorine Gallium Helium Iridium Krypton Palladium Radon \
	Rubidium Selenium Strontium Thallium Xenon Zirconium
RINGTONE_FILES := Sceptrum
EFFECT_FILES := camera_focus Dock Effect_Tick KeypressStandard KeypressSpacebar KeypressDelete KeypressInvalid \
	KeypressReturn Lock Trusted Undock Unlock 
MATERIAL_EFFECT_FILES := camera_click LowBattery VideoRecord VideoStop

PRODUCT_COPY_FILES += $(foreach fn,$(ALARM_FILES),\
	$(LOCAL_PATH)/alarms/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(NOTIFICATION_FILES),\
	$(LOCAL_PATH)/notifications/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(RINGTONE_FILES),\
	$(LOCAL_PATH)/ringtones/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(EFFECT_FILES),\
	$(LOCAL_PATH)/effects/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)
PRODUCT_COPY_FILES += $(foreach fn,$(MATERIAL_EFFECT_FILES),\
	$(LOCAL_PATH)/effects/material/ogg/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)