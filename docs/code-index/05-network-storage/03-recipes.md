# 05-03 · 菜谱发布、上传与写操作

> 返回分类首页：[05-network-storage.md](../05-network-storage.md)。基础网络/认证/存储见 [01-foundation-auth.md](01-foundation-auth.md)，用户/好友关系见 [02-relation.md](02-relation.md)。

**收录范围：**我的菜谱列表与详情、创建/更新/删除、图片上传、上传约束、菜谱写请求与上传响应。

**索引原则：**记录鉴权、分页、上传边界、204 校验与权限限制；草稿可见性、菜谱所有权与预订资格以后端判定为准。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| DATA-022 | `RecipeApi.listMine/get/create/update/delete`（suspend） | 我的菜谱列表、详情、创建/更新/删除接口；菜谱写操作优先复用。 | Retrofit `suspend`，必须协程调用；baseUrl 含 `/api/v1/`，路径为 `recipes/mine`、`recipes/{id}`、`recipes`、`recipes/{id}`。`listMine` 的 status 由 DATA-024 限定 `published/draft/all`，分页 page≥1、pageSize 1..50；`DELETE` 返回 204，使用 `Response<Unit>`，必须经 DATA-019 `requireSuccessful`；其他非 2xx 由 DATA-018 映射。DATA-004 在有 token 时附加凭证；`get` 无凭证仅可读 public/published，好友内容与草稿对匿名返回 404，提供无效凭证仍 401；其余入口需登录，后端校验所有权/可见性。 | `subModules/lib_network/src/main/java/com/example/network/api/RecipeApi.kt` | `RecipeRepository`（DATA-024）调用；`CookBookChildFragment`、`AddRecipesFragment` 间接使用。`P03-T05` 仅校对详情的后端契约，Android API 入口未变、详情页面仍未实现。 |
| DATA-023 | `UploadApi.upload`（multipart） | 菜谱图片上传；multipart 字段名固定为 `file`。 | `@Multipart @POST uploads`，参数为 `MultipartBody.Part`；需要登录，文件名与 MIME 由 DATA-025 清洗/校验。返回 DATA-026 `UploadResponse.url`，通常是 `/uploads/...` 原始路径，展示前必须经 DATA-003 `resolveMediaUrl`。客户端只做防御性校验，后端仍是最终校验方；上传未完成前不得保存表单，避免写入不存在的图片 URL。 | `subModules/lib_network/src/main/java/com/example/network/api/UploadApi.kt` | `RecipeRepository.uploadImage`（DATA-024）；`AddRecipesFragment.uploadPickedImage` 调用。 |
| DATA-024 | `RecipeRepository` | 菜谱数据访问层：我的菜谱、详情、创建/更新/删除、图片上传与参数归一化。 | 方法均为 `suspend`；`listMine` 的 status 仅允许 `published/draft/all`（`require` 抛 `IllegalArgumentException`），page≥1、pageSize 1..50、默认 20（`DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE`）；`get/update/delete` 要求 id>0；`delete` 用 `requireSuccessful` 校验 204；`uploadImage` 要求 bytes 非空、MIME/大小合法，上传响应 URL 为空时 `check` 抛 `IllegalStateException`。返回原始路径，不自动 resolve；无缓存/重试/请求去重；异常经 DATA-019。 | `subModules/lib_network/src/main/java/com/example/network/repository/RecipeRepository.kt` | `CookBookChildFragment` 用 `listMine/delete`；`AddRecipesFragment` 用 `get/create/update/uploadImage`；常量 `STATUS_PUBLISHED/STATUS_DRAFT/STATUS_ALL`。 |
| DATA-025 | `RecipeMediaRules` | 菜谱图片上传约束：jpeg/png/webp、1..5 MiB、扩展名兜底与上传文件名清洗；供表单预校验与 Repository 双重校验复用。 | Kotlin `object`；`MAX_IMAGE_BYTES = 5 MiB`；`isAllowedMimeType` 先 trim+lowercase，null/空白返回 false；`isWithinSizeLimit` 接受 1..上限，0 字节无效；`mimeTypeForFileName` 支持 jpg/jpeg/png/webp，其他返回 null；`uploadFileName` 去掉路径分隔符并按 MIME 回退 `recipe.jpg/png/webp`。不支持 GIF/HEIC；客户端校验不替代后端。 | `subModules/lib_network/src/main/java/com/example/network/RecipeMediaRules.kt` | `RecipeRepository.uploadImage`（DATA-024）；`AddRecipesFragment.resolveMimeType/uploadPickedImage`；`RecipeMediaRulesTest` 5 例（TEST-012）。 |
| DATA-026 | `RecipeWriteBody`、`UploadResponse` | 创建/更新菜谱请求体与图片上传响应。 | `RecipeWriteBody` 字段：name、description?、imageUrl?、category、visibility、status?、ingredients、steps。可空字段为 null 时由 Gson 省略；表单用空字符串显式清空 description/imageUrl，确保 PATCH 覆盖旧值。`status` 为 published/draft，缺省时后端按 published；`visibility` 为 friends/public。`UploadResponse.url` 默认空串，展示前经 DATA-003 解析。 | `subModules/lib_network/src/main/java/com/example/network/bean/RecipeWriteBody.kt` | `RecipeRepository.create/update`；`AddRecipesFragment.saveRecipe` 构造 body；`UploadApi` 返回 `UploadResponse`（同目录 `UploadResponse.kt`）。 |

> 跨分类引用：菜谱表单校验见 BIZ-007，固定选项与 visibility/status 映射见 BIZ-008；列表/表单页面与 `KEY_BUNDLE_RECIPE_ID` 见 ARCH-013、ARCH-014；`RecipeDto.status` 见 DATA-017。
>
> 未覆盖：菜谱详情页、图片裁剪/压缩、上传进度回调、并发上传、断点续传与草稿自动保存尚未实现；`RecipeMediaRules` 不校验图片实际解码内容，只校验 MIME 与字节大小。
