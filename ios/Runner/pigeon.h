// Autogenerated from Pigeon (v10.1.2), do not edit directly.
// See also: https://pub.dev/packages/pigeon

#import <Foundation/Foundation.h>

@protocol FlutterBinaryMessenger;
@protocol FlutterMessageCodec;
@class FlutterError;
@class FlutterStandardTypedData;

NS_ASSUME_NONNULL_BEGIN


/// The codec used by BiometricsApi.
NSObject<FlutterMessageCodec> *BiometricsApiGetCodec(void);

@protocol BiometricsApi
- (void)invokeDiscoverSbiFieldId:(NSString *)fieldId modality:(NSString *)modality completion:(void (^)(NSString *_Nullable, FlutterError *_Nullable))completion;
- (void)getBestBiometricsFieldId:(NSString *)fieldId modality:(NSString *)modality completion:(void (^)(NSArray<NSString *> *_Nullable, FlutterError *_Nullable))completion;
- (void)extractImageValuesFieldId:(NSString *)fieldId modality:(NSString *)modality completion:(void (^)(NSArray<FlutterStandardTypedData *> *_Nullable, FlutterError *_Nullable))completion;
@end

extern void BiometricsApiSetup(id<FlutterBinaryMessenger> binaryMessenger, NSObject<BiometricsApi> *_Nullable api);

NS_ASSUME_NONNULL_END
