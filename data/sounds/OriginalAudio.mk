#
# Original audio package that shipped on G1
#
# This file is included from core.mk so that all devices will have these sounds
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Alarm_Buzzer Alarm_Beep_01 Alarm_Beep_02 Alarm_Classic Alarm_Beep_03 Alarm_Rooster_02
NOTIFICATION_FILES := Beat_Box_Android F1_MissedCall F1_New_MMS F1_New_SMS Heaven TaDa Tinkerbell
RINGTONE_FILES := Ring_Classic_02 Ring_Digital_02 Ring_Synth_02 Ring_Synth_04
EFFECT_FILES := camera_click Effect_Tick KeypressStandard KeypressSpacebar KeypressDelete KeypressReturn VideoRecord VideoStop 

PRODUCT_COPY_FILES += $(foreach fn,$(ALARM_FILES),\
	$(LOCAL_PATH)/alarms/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(NOTIFICATION_FILES),\
	$(LOCAL_PATH)/notifications/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(RINGTONE_FILES),\
	$(LOCAL_PATH)/ringtones/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)
	
PRODUCT_COPY_FILES += $(foreach fn,$(EFFECT_FILES),\
$(LOCAL_PATH)/effects/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/$(fn).ogg)

# Minimal NewWaveLabs sounds
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/newwavelabs/BeatPlucker.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/BeatPlucker.ogg \
	$(LOCAL_PATH)/newwavelabs/CaffeineSnake.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/CaffeineSnake.ogg


ifneq ($(MINIMAL_NEWWAVELABS),true)
	NEWWAVELABS_RINGTONE_FILES := BentleyDubs BirdLoop CaribbeanIce CrazyDream CurveBall DreamTheme EtherShake FriendlyGhost \
	GameOverGuitar Growl InsertCoin LoopyLounge LoveFlute MidEvilJaunt MildlyAlarming NewPlayer Noises1 Noises2 Noises3 OrganDub \
	RomancingTheTone SitarVsSitar SpringyJalopy Terminated TwirlAway VeryAlarmed World
	
	NEWWAVELABS_NOTIFICATION_FILES := DearDeer DontPanic Highwire KzurbSonar OnTheHunt Voila
		
	PRODUCT_COPY_FILES += $(foreach fn,$(NEWWAVELABS_NOTIFICATION_FILES),\
		$(LOCAL_PATH)/newwavelabs/notifications/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)
	
	PRODUCT_COPY_FILES += $(foreach fn,$(NEWWAVELABS_RINGTONE_FILES),\
		$(LOCAL_PATH)/newwavelabs/ringtones/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)
endif
