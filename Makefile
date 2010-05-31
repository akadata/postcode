# This should be the Android SDK root directory
ANDROID ?= ../../../android-sdk-linux_86/

# SDK version
ANDROID_VERSION=1.5

# NDK has other versioning scheme, but it's translatable
ifeq ("x$(ANDROID_VERSION)", "x1.5")
ANDROID_NDK_VERSION=3
else
$(error Don\'t have NDK mapping for version $(ANDROID_VERSION))
endif

PLATFORM_PATH=$(ANDROID)/platforms/android-$(ANDROID_NDK_VERSION)

TARGET = Postcode
TARGET_FULLNAME = $(TARGET).apk
KEYSTORE=.debug.keystore

R_PATH = src/net/tevp/postcode/R.java

SOURCE_FILES=$(wildcard src/net/tevp/*/*.java) $(R_PATH)
BIN_FILES=$(patsubst %.java, %.class, $(patsubst src/%,bin/%,$(SOURCE_FILES)))

.PHONY: install all

all: $(TARGET_FULLNAME)

install: $(TARGET_FULLNAME)
	$(ANDROID)/tools/adb install -r $<

log:
	$(ANDROID)/tools/adb logcat

$(TARGET_FULLNAME): $(TARGET)_unaligned.apk
	rm -f $@
	$(ANDROID)/tools/zipalign 4 $< $@

$(KEYSTORE):
	keytool -genkeypair -keystore $(KEYSTORE) -validity 730 -keypass android -storepass android -dname "CN=Android Debug,O=Android,C=UK"

$(TARGET)_unaligned.apk: $(TARGET)_unsigned.apk $(KEYSTORE)
	jarsigner -keystore $(KEYSTORE) -storepass android -signedjar $@ $< mykey

$(TARGET)_unsigned.apk: classes.dex resources.apk
	$(ANDROID)/tools/apkbuilder $@ -u -f $< -z resources.apk -rf src -v

# to raise the memory limit, add -JXmx512M
classes.dex: bin-stamp
	$(PLATFORM_PATH)/tools/dx --dex --verbose --output=$@ bin

resources.apk: res/drawable/icon.png res/values/strings.xml \
		$(wildcard res/layout/*.xml) AndroidManifest.xml $(wildcard res/values/*.xml)
	$(PLATFORM_PATH)/tools/aapt package -f -M AndroidManifest.xml \
		-S res -I $(PLATFORM_PATH)/android.jar -F $@

$(R_PATH): res/drawable/icon.png \
		AndroidManifest.xml \
		res/values/* $(wildcard res/layout/*.xml)
	$(PLATFORM_PATH)/tools/aapt package -m -J src \
		-M AndroidManifest.xml -S res \
		-I $(PLATFORM_PATH)/android.jar

bin:
	mkdir -p $@
	mkdir -p lib/armeabi

bin-stamp: bin $(BIN_FILES)
	touch $@

$(BIN_FILES): $(SOURCE_FILES)
	javac -source 1.5 -target 1.5 -cp $(PLATFORM_PATH)/android.jar \
		-d bin $< $(SOURCE_FILES)
