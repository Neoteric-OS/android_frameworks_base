#
# Audio Package 3
#
# Include this file in a product makefile to include these audio files
#
# This is a larger package of sounds than the 1.0 release for devices
# that have larger internal flash.
#

LOCAL_PATH := frameworks/base/data/sounds

ALARM_FILES := Alarm_Buzzer Alarm_Beep_01 Alarm_Beep_02 Alarm_Classic Alarm_Beep_03 Alarm_Rooster_02
NOTIFICATION_FILES := Beat_Box_Android F1_MissedCall F1_New_MMS F1_New_SMS Heaven moonbeam pixiedust pizzicato TaDa Tinkerbell tweeters
RINGTONE_FILES := Ring_Classic_02 Ring_Digital_02 Ring_Synth_02 Ring_Synth_04

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

# Minimal NewWaveLabs sounds
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/newwavelabs/BeatPlucker.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/BeatPlucker.ogg \
	$(LOCAL_PATH)/newwavelabs/CaffeineSnake.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/CaffeineSnake.ogg

ifneq ($(MINIMAL_NEWWAVELABS),true)
	NEWWAVELABS_NOTIFICATION_FILES := DearDeer DontPanic Highwire KzurbSonar OnTheHunt Voila
	
	NEWWAVELABS_RINGTONE_FILES := BentleyDubs BirdLoop CaribbeanIce CrazyDream CurveBall DreamTheme EtherShake FriendlyGhost \
		GameOverGuitar Growl InsertCoin LoopyLounge LoveFlute MidEvilJaunt MildlyAlarming NewPlayer Noises1 Noises2 Noises3 OrganDub \
		RomancingTheTone SitarVsSitar SpringyJalopy Terminated TwirlAway VeryAlarmed World Big_Easy Bollywood Cairo Calypso_Steel \
		Champagne_Edition Club_Cubano Eastern_Sky Funk_Yall Savannah Gimme_Mo_Town Glacial_Groove Seville No_Limits Revelation \
		Paradise_Island Road_Trip Shes_All_That Steppin_Out Third_Eye Thunderfoot HalfwayHome CrayonRock DancinFool BussaMove DonMessWivIt \
		SilkyWay Playa
	
	PRODUCT_COPY_FILES += $(foreach fn,$(NEWWAVELABS_NOTIFICATION_FILES),\
		$(LOCAL_PATH)/newwavelabs/notifications/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/$(fn).ogg)

	PRODUCT_COPY_FILES += $(foreach fn,$(NEWWAVELABS_RINGTONE_FILES),\
		$(LOCAL_PATH)/newwavelabs/ringtones/$(fn).ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/$(fn).ogg)

endif
