// JNI bridge: runs COLMAP's automatic reconstruction on a folder of frames and
// reports how many models were produced. Dense output is written to
// <workspace>/dense/0/meshed-poisson.ply by COLMAP itself.
#include <jni.h>

#include <memory>
#include <string>

#include "colmap/controllers/automatic_reconstruction.h"
#include "colmap/scene/reconstruction_manager.h"

namespace {

std::string ToStdString(JNIEnv* env, jstring value) {
  const char* chars = env->GetStringUTFChars(value, nullptr);
  std::string out(chars);
  env->ReleaseStringUTFChars(value, chars);
  return out;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_photoscan_app_pipeline_NativeColmap_nativeReconstruct(
    JNIEnv* env,
    jclass /*clazz*/,
    jstring image_dir,
    jstring workspace_dir,
    jboolean low_quality) {
  using Controller = colmap::AutomaticReconstructionController;

  Controller::Options options;
  options.image_path = ToStdString(env, image_dir);
  options.workspace_path = ToStdString(env, workspace_dir);
  // Frames come from one video, so sequential matching and one camera apply.
  options.data_type = Controller::DataType::VIDEO;
  options.quality = low_quality ? Controller::Quality::LOW
                                : Controller::Quality::MEDIUM;
  options.single_camera = true;
  options.camera_model = "SIMPLE_RADIAL";
  options.feature = Controller::Feature::SIFT;
  options.mapper = Controller::Mapper::INCREMENTAL;
  options.mesher = Controller::Mesher::POISSON;
  options.dense = true;          // CPU PatchMatch; the Android build has no CUDA.
  options.use_gpu = false;
  options.num_threads = -1;      // Use all cores.

  auto manager = std::make_shared<colmap::ReconstructionManager>();
  Controller controller(options, manager);
  controller.Start();
  controller.Wait();
  return static_cast<jint>(manager->Size());
}
