// SPDX-License-Identifier: BSD-3-Clause
//
// photoscan: replacement for COLMAP's sensor/bitmap.cc that does not depend on
// OpenImageIO. Used only by the Android build (see android/native/README.md).
//
// Differences from upstream, which matter for the Android pipeline:
//  - Decoding and encoding use stb_image / stb_image_write (JPEG, PNG, BMP, TGA).
//  - Colour is treated as sRGB throughout; no linearisation is performed.
//  - EXIF is not read, so the Exif*() accessors return std::nullopt. Video
//    frames from Android do not carry EXIF, so the upstream priors are unused.
//  - Metadata is stored as strings in a map, not as OIIO attributes.

#include "colmap/sensor/bitmap.h"

#include "colmap/util/file.h"
#include "colmap/util/logging.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <exception>
#include <map>
#include <string>

#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include <stb_image_write.h>
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include <stb_image_resize2.h>

namespace colmap {
namespace {

struct StringMetaData : public Bitmap::MetaData {
  std::map<std::string, std::string> attributes;
  int jpeg_quality = 95;
};

StringMetaData* Upcast(Bitmap::MetaData* meta_data) {
  return dynamic_cast<StringMetaData*>(meta_data);
}

std::unique_ptr<Bitmap::MetaData> CloneMeta(const Bitmap::MetaData* meta_data) {
  if (meta_data == nullptr) {
    return nullptr;
  }
  const auto* source = dynamic_cast<const StringMetaData*>(meta_data);
  THROW_CHECK_NOTNULL(source);
  auto cloned = std::make_unique<StringMetaData>();
  cloned->attributes = source->attributes;
  cloned->jpeg_quality = source->jpeg_quality;
  return cloned;
}

}  // namespace

Bitmap::Bitmap() : width_(0), height_(0), channels_(0), linear_colorspace_(false) {}

Bitmap::Bitmap(const int width,
               const int height,
               const bool as_rgb,
               const bool linear_colorspace)
    : width_(width),
      height_(height),
      channels_(as_rgb ? 3 : 1),
      linear_colorspace_(linear_colorspace),
      data_(static_cast<size_t>(width) * height * (as_rgb ? 3 : 1)),
      meta_data_(std::make_unique<StringMetaData>()) {}

Bitmap::Bitmap(const Bitmap& other)
    : width_(other.width_),
      height_(other.height_),
      channels_(other.channels_),
      linear_colorspace_(other.linear_colorspace_),
      data_(other.data_),
      meta_data_(CloneMeta(other.meta_data_.get())) {}

Bitmap::Bitmap(Bitmap&& other) noexcept
    : width_(other.width_),
      height_(other.height_),
      channels_(other.channels_),
      linear_colorspace_(other.linear_colorspace_),
      data_(std::move(other.data_)),
      meta_data_(std::move(other.meta_data_)) {
  other.width_ = 0;
  other.height_ = 0;
  other.channels_ = 0;
}

Bitmap& Bitmap::operator=(const Bitmap& other) {
  if (this != &other) {
    width_ = other.width_;
    height_ = other.height_;
    channels_ = other.channels_;
    linear_colorspace_ = other.linear_colorspace_;
    data_ = other.data_;
    meta_data_ = CloneMeta(other.meta_data_.get());
  }
  return *this;
}

Bitmap& Bitmap::operator=(Bitmap&& other) noexcept {
  if (this != &other) {
    width_ = other.width_;
    height_ = other.height_;
    channels_ = other.channels_;
    linear_colorspace_ = other.linear_colorspace_;
    data_ = std::move(other.data_);
    meta_data_ = std::move(other.meta_data_);
    other.width_ = 0;
    other.height_ = 0;
    other.channels_ = 0;
  }
  return *this;
}

void Bitmap::Fill(const BitmapColor<uint8_t>& color) {
  if (IsGrey()) {
    std::fill(data_.begin(), data_.end(), color.r);
  } else {
    THROW_CHECK_EQ(data_.size() % 3, 0);
    for (size_t i = 0; i < data_.size(); i += 3) {
      data_[i] = color.r;
      data_[i + 1] = color.g;
      data_[i + 2] = color.b;
    }
  }
}

// EXIF is not read in this build; see the file comment.
std::optional<int> Bitmap::ExifOrientation() const { return std::nullopt; }
std::optional<std::string> Bitmap::ExifCameraModel() const { return std::nullopt; }
std::optional<double> Bitmap::ExifFocalLength() const { return std::nullopt; }
std::optional<double> Bitmap::ExifLatitude() const { return std::nullopt; }
std::optional<double> Bitmap::ExifLongitude() const { return std::nullopt; }
std::optional<double> Bitmap::ExifAltitude() const { return std::nullopt; }

bool Bitmap::Read(const std::filesystem::path& path,
                  const bool as_rgb,
                  const bool linearize_colorspace) {
  (void)linearize_colorspace;  // Colour is treated as sRGB; see file comment.
  if (!ExistsFile(path)) {
    VLOG(3) << "Failed to read bitmap, because file does not exist";
    return false;
  }

  // Ask stb for the target channel count directly; it converts grey<->RGB and
  // drops alpha.
  const int desired_channels = as_rgb ? 3 : 1;
  int width = 0;
  int height = 0;
  int file_channels = 0;
  FILE* file = std::fopen(path.string().c_str(), "rb");
  if (file == nullptr) {
    return false;
  }
  unsigned char* pixels = stbi_load_from_file(file, &width, &height, &file_channels,
                                              desired_channels);
  std::fclose(file);
  if (pixels == nullptr) {
    VLOG(3) << "Failed to read bitmap: " << stbi_failure_reason();
    return false;
  }

  width_ = width;
  height_ = height;
  channels_ = desired_channels;
  linear_colorspace_ = false;
  data_.assign(pixels, pixels + static_cast<size_t>(width) * height * channels_);
  stbi_image_free(pixels);
  meta_data_ = std::make_unique<StringMetaData>();
  return true;
}

bool Bitmap::Write(const std::filesystem::path& path,
                   const bool delinearize_colorspace) const {
  (void)delinearize_colorspace;
  if (IsEmpty()) {
    return false;
  }
  const std::string utf8_path = path.string();
  const std::string extension = path.extension().string();
  int ok = 0;
  if (extension == ".jpg" || extension == ".jpeg") {
    int quality = 100;
    if (const auto* meta = dynamic_cast<const StringMetaData*>(meta_data_.get())) {
      quality = meta->jpeg_quality;
    }
    ok = stbi_write_jpg(utf8_path.c_str(), width_, height_, channels_, data_.data(),
                        quality);
  } else if (extension == ".png") {
    ok = stbi_write_png(utf8_path.c_str(), width_, height_, channels_, data_.data(),
                        width_ * channels_);
  } else if (extension == ".bmp") {
    ok = stbi_write_bmp(utf8_path.c_str(), width_, height_, channels_, data_.data());
  } else if (extension == ".tga") {
    ok = stbi_write_tga(utf8_path.c_str(), width_, height_, channels_, data_.data());
  } else {
    VLOG(3) << "Unsupported output format: " << extension;
    return false;
  }
  return ok != 0;
}

void Bitmap::SetImageData(int width, int height, std::vector<uint8_t>&& data) {
  width_ = width;
  height_ = height;
  data_ = std::move(data);
}

void Bitmap::Rescale(const int new_width,
                     const int new_height,
                     RescaleFilter filter) {
  THROW_CHECK_GT(new_width, 0);
  THROW_CHECK_GT(new_height, 0);
  std::vector<uint8_t> new_data(static_cast<size_t>(new_width) * new_height * channels_);
  const int stride = width_ * channels_;
  const int new_stride = new_width * channels_;
  const stbir_filter stbir_filter_kernel =
      filter == RescaleFilter::kBox ? STBIR_FILTER_BOX : STBIR_FILTER_TRIANGLE;
  const int result = stbir_resize_uint8_generic(
      data_.data(), width_, height_, stride,
      new_data.data(), new_width, new_height, new_stride,
      channels_, /*alpha_channel=*/STBIR_ALPHA_CHANNEL_NONE, /*flags=*/0,
      STBIR_EDGE_CLAMP, stbir_filter_kernel, STBIR_COLORSPACE_LINEAR,
      /*alloc_context=*/nullptr);
  THROW_CHECK_EQ(result, 1);
  SetImageData(new_width, new_height, std::move(new_data));
}

double Bitmap::Thumbnail(const int max_image_size, RescaleFilter filter) {
  THROW_CHECK_GT(max_image_size, 0);
  if (width_ <= max_image_size && height_ <= max_image_size) {
    return 1.0;
  }
  // Fit the down-sampled version exactly into the max dimensions.
  const double scale =
      static_cast<double>(max_image_size) / std::max(width_, height_);
  Rescale(std::max(1, static_cast<int>(std::round(width_ * scale))),
          std::max(1, static_cast<int>(std::round(height_ * scale))), filter);
  return scale;
}

void Bitmap::Rot90(int k) {
  if (IsEmpty()) {
    return;
  }
  k = k % 4;
  if (k < 0) {
    k += 4;
  }
  if (k == 0) {
    return;
  }

  // Counter-clockwise rotation of a W x H image; (x, y) is the source pixel.
  const int W = width_;
  const int H = height_;
  const bool swap_dims = (k == 1 || k == 3);
  const int new_width = swap_dims ? H : W;
  const int new_height = swap_dims ? W : H;
  std::vector<uint8_t> new_data(static_cast<size_t>(new_width) * new_height * channels_);

  for (int y = 0; y < H; ++y) {
    for (int x = 0; x < W; ++x) {
      int nx = 0;
      int ny = 0;
      if (k == 1) {  // 90 CCW: top-left goes to bottom-left.
        nx = y;
        ny = W - 1 - x;
      } else if (k == 2) {  // 180.
        nx = W - 1 - x;
        ny = H - 1 - y;
      } else {  // k == 3, 270 CCW: top-left goes to top-right.
        nx = H - 1 - y;
        ny = x;
      }
      const size_t src = (static_cast<size_t>(y) * W + x) * channels_;
      const size_t dst = (static_cast<size_t>(ny) * new_width + nx) * channels_;
      std::memcpy(&new_data[dst], &data_[src], channels_);
    }
  }
  SetImageData(new_width, new_height, std::move(new_data));
}

void Bitmap::Crop(const int left,
                  const int top,
                  const int width,
                  const int height) {
  THROW_CHECK_GE(left, 0);
  THROW_CHECK_GE(top, 0);
  THROW_CHECK_GT(width, 0);
  THROW_CHECK_GT(height, 0);
  THROW_CHECK_LE(left + width, width_);
  THROW_CHECK_LE(top + height, height_);
  if (left == 0 && top == 0 && width == width_ && height == height_) {
    return;
  }

  std::vector<uint8_t> cropped(static_cast<size_t>(width) * height * channels_);
  const int src_pitch = Pitch();
  const int dst_pitch = width * channels_;
  for (int y = 0; y < height; ++y) {
    std::memcpy(cropped.data() + y * dst_pitch,
                data_.data() + (top + y) * src_pitch + left * channels_,
                dst_pitch);
  }
  SetImageData(width, height, std::move(cropped));
}

Bitmap Bitmap::Clone() const { return *this; }

Bitmap Bitmap::CloneAsGrey() const {
  if (IsGrey()) {
    return Clone();
  }
  Bitmap cloned;
  cloned.width_ = width_;
  cloned.height_ = height_;
  cloned.channels_ = 1;
  cloned.linear_colorspace_ = linear_colorspace_;
  cloned.data_.resize(static_cast<size_t>(width_) * height_);
  for (size_t i = 0; i < cloned.data_.size(); ++i) {
    // Rec. 709 luma weights, as upstream.
    // NOLINTNEXTLINE(bugprone-incorrect-roundings)
    cloned.data_[i] = static_cast<uint8_t>(.2126f * data_[3 * i + 0] +
                                           .7152f * data_[3 * i + 1] +
                                           .0722f * data_[3 * i + 2] + .5f);
  }
  cloned.meta_data_ = CloneMeta(meta_data_.get());
  return cloned;
}

Bitmap Bitmap::CloneAsRGB() const {
  if (IsRGB()) {
    return Clone();
  }
  THROW_CHECK_EQ(channels_, 1);
  Bitmap cloned;
  cloned.width_ = width_;
  cloned.height_ = height_;
  cloned.channels_ = 3;
  cloned.linear_colorspace_ = linear_colorspace_;
  cloned.data_.resize(static_cast<size_t>(width_) * height_ * 3);
  for (size_t i = 0; i < data_.size(); ++i) {
    cloned.data_[3 * i + 0] = data_[i];
    cloned.data_[3 * i + 1] = data_[i];
    cloned.data_[3 * i + 2] = data_[i];
  }
  cloned.meta_data_ = CloneMeta(meta_data_.get());
  return cloned;
}

void Bitmap::SetJpegQuality(int quality) {
  THROW_CHECK_GT(quality, 0);
  THROW_CHECK_LE(quality, 100);
  if (auto* meta = Upcast(meta_data_.get())) {
    meta->jpeg_quality = quality;
  }
  SetMetaData("Compression", "jpeg:" + std::to_string(quality));
}

void Bitmap::SetMetaData(const std::string_view& name,
                         const std::string_view& type,
                         const void* value) {
  THROW_CHECK_NE(type, "string");
  auto* meta = Upcast(meta_data_.get());
  THROW_CHECK_NOTNULL(meta);
  if (type == "int") {
    meta->attributes[std::string(name)] = std::to_string(*static_cast<const int*>(value));
  } else if (type == "float") {
    meta->attributes[std::string(name)] = std::to_string(*static_cast<const float*>(value));
  } else if (type == "double") {
    meta->attributes[std::string(name)] = std::to_string(*static_cast<const double*>(value));
  } else {
    THROW_CHECK(false) << "Unsupported metadata type: " << type;
  }
}

void Bitmap::SetMetaData(const std::string_view& name,
                         const std::string_view& value) {
  auto* meta = Upcast(meta_data_.get());
  THROW_CHECK_NOTNULL(meta);
  meta->attributes[std::string(name)] = std::string(value);
}

bool Bitmap::GetMetaData(const std::string_view& name,
                         const std::string_view& type,
                         void* value) const {
  THROW_CHECK_NE(type, "string");
  const auto* meta = dynamic_cast<const StringMetaData*>(meta_data_.get());
  if (meta == nullptr) {
    return false;
  }
  const auto it = meta->attributes.find(std::string(name));
  if (it == meta->attributes.end()) {
    return false;
  }
  try {
    if (type == "int") {
      *static_cast<int*>(value) = std::stoi(it->second);
    } else if (type == "float") {
      *static_cast<float*>(value) = std::stof(it->second);
    } else if (type == "double") {
      *static_cast<double*>(value) = std::stod(it->second);
    } else {
      return false;
    }
  } catch (const std::exception&) {
    return false;
  }
  return true;
}

std::optional<std::string> Bitmap::GetMetaData(const std::string_view& name) const {
  const auto* meta = dynamic_cast<const StringMetaData*>(meta_data_.get());
  if (meta == nullptr) {
    return std::nullopt;
  }
  const auto it = meta->attributes.find(std::string(name));
  if (it == meta->attributes.end()) {
    return std::nullopt;
  }
  return it->second;
}

void Bitmap::CloneMetadata(Bitmap* target) const {
  THROW_CHECK_NOTNULL(target);
  target->meta_data_ = CloneMeta(meta_data_.get());
}

std::ostream& operator<<(std::ostream& stream, const Bitmap& bitmap) {
  stream << "Bitmap(width=" << bitmap.Width() << ", height=" << bitmap.Height()
         << ", channels=" << bitmap.Channels() << ")";
  return stream;
}

float JetColormap::Red(const float gray) { return Base(gray - 0.25f); }

float JetColormap::Green(const float gray) { return Base(gray); }

float JetColormap::Blue(const float gray) { return Base(gray + 0.25f); }

BitmapColor<uint8_t> JetColormap::ToBitmapColor(const float gray) {
  return BitmapColor<float>(255 * Red(gray), 255 * Green(gray), 255 * Blue(gray))
      .Cast<uint8_t>();
}

float JetColormap::Base(const float val) {
  // NOLINTNEXTLINE(bugprone-branch-clone)
  if (val <= 0.125f) {
    return 0.0f;
  } else if (val <= 0.375f) {
    return Interpolate(2.0f * val - 1.0f, 0.0f, -0.75f, 1.0f, -0.25f);
  } else if (val <= 0.625f) {
    return 1.0f;
  } else if (val <= 0.87f) {
    return Interpolate(2.0f * val - 1.0f, 1.0f, 0.25f, 0.0f, 0.75f);
  } else {
    return 0.0f;
  }
}

float JetColormap::Interpolate(const float val,
                               const float y0,
                               const float x0,
                               const float y1,
                               const float x1) {
  return (val - x0) * (y1 - y0) / (x1 - x0) + y0;
}

}  // namespace colmap
