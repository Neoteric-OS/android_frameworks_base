#
# Audio Package 5 - Crespo/Soju
#
# Include this file in a product makefile to include these audio files
#
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Alarm_Buzzer Alarm_Beep_01 Alarm_Beep_02 Alarm_Classic Alarm_Beep_03 Alarm_Rooster_02
NOTIFICATION_FILES := Aldebaran Altair Antares arcturus Betelgeuse Canopus Capella Castor CetiAlpha Deneb Electra Fomalhaut Merope \
	Polaris Pollux Procyon regulus sirius Sirrah vega
RINGTONE_FILES := ANDROMEDA Aquila ArgoNavis BOOTES CANISMAJOR Carina CASSIOPEIA Centaurus Cygnus Draco Eridani hydra Lyra Machina \
	Orion Pegasus PERSEUS Pyxis Rigel Scarabaeus Sceptrum Solarium Testudo URSAMINOR Vespa

EFFECT_FILES := camera_click Dock Effect_Tick KeypressStandard KeypressSpacebar KeypressDelete KeypressInvalid KeypressReturn \
	Lock LowBattery Undock Unlock VideoRecord VideoStop

PRODUCT_COPY_FILES += $(foreach fn,$(ALARM_FILES),\
		$(LOCAL_PATH)/alarms/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/$(fn).ogg)
PRODUCT_COPY_FILES += $(foreach fn,$(NOTIFICATION_FILES),\
			$(LOCAL_PATH)/notifications/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)
PRODUCT_COPY_FILES += $(foreach fn,$(RINGTONE_FILES),\
	$(LOCAL_PATH)/ringtones/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)
	
PRODUCT_COPY_FILES += $(foreach fn,$(EFFECT_FILES),\
	$(LOCAL_PATH)/effects/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)
PRODUCT_COPY_FILES += \
		$(LOCAL_PATH)/effects/ogg/Trusted.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Trusted.ogg