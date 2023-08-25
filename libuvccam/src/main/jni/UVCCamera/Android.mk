LOCAL_PATH := $(call my-dir)
#OpenCV_DIR := $(LOCAL_PATH)/../../../opencv/native
OpenCV_DIR := $(LOCAL_PATH)/../../../../../opencv/native
#OpenCV_DIR := C:/Users/User/OpenCV-android-sdk/sdk/native

#include ${LOCAL_PATH}/../../../../../opencv/native/jni/OpenCV.mk

include $(CLEAR_VARS)

######################################################################
# Make shared library libUVCCamera.so
######################################################################
CFLAGS := -Werror

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/ \
    $(LOCAL_PATH)/../ \
    $(LOCAL_PATH)/../rapidjson/include \

OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=STATIC
include ${OpenCV_DIR}\jni\OpenCV.mk

#LOCAL_MODULE += myModule
#LOCAL_SRC_FILES += $(PROJ_PATH)/UVCCamera/UVCPreview.cpp

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_CFLAGS += -DACCESS_RAW_DESCRIPTORS
LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays

LOCAL_CFLAGS += -std=c++11 -frtti -fexceptions -fopenmp -w


LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -ldl
LOCAL_LDLIBS += -llog
LOCAL_LDLIBS += -landroid
LOCAL_LDLIBS += -lz

#LOCAL_MODULE    += opencv
#
#include $(PREBUILT_STATIC_LIBRARY)


#LOCAL_LDFLAGS += -fopenmp

LOCAL_SHARED_LIBRARIES += usb1.0 uvc

LOCAL_ARM_MODE := arm

LOCAL_SRC_FILES := \
    _onload.cpp \
    utilbase.cpp \
    UVCCamera.cpp \
    UVCControl.cpp \
    UVCPreview.cpp \
    UVCButtonCallback.cpp \
    UVCStatusCallback.cpp \
    Parameters.cpp \
    registerUVCCamera.cpp \
    registerUVCControl.cpp

LOCAL_MODULE    := UVCCamera
include $(BUILD_SHARED_LIBRARY)