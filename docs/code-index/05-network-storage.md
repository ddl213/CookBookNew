# 05 · 网络、存储与异步能力

> 本类盘点状态：已盘点。已核对 `lib_network`、`lib_cookbook` 的 Room 依赖与 `lib_android_common` 的存储工具。当前**没有网络请求封装、Repository、DAO 或可用的数据库实现**；以下只登记真实存在的数据模型与数据库骨架。

**收录范围：**API/网络包装、Repository、Room/本地数据、缓存、协程/异步共用封装。

**索引原则：**记录权限、异常/重试、线程和数据安全边界，不擅自推断真实接口；骨架与未接线能力必须注明。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| DATA-001 | 数据模型 `Recipe`、`User`、`HomeFeedItem`、`Following` | 页面展示用的菜谱、用户、好友动态、关注关系数据载体；可作为后续接口 DTO 的起点。 | 纯 Kotlin `data class`，无序列化注解、无网络字段映射；`Recipe` 包含 id/userId/name/imageUrl/desc/publishDate/likes/collections/commentsCount；`User` 包含 bio/fans/followingCount/recipesCount/isFollowing。当前仅被本地假数据构造，不能据此认定已对接后端。 | `subModules/lib_network/src/main/java/com/example/network/bean/Recipe.kt` | `CookBookChildFragment`、`IndexChildFragment` 用硬编码列表构造 `Recipe`、`User`、`HomeFeedItem`；`Following` 仅定义，无调用。同目录 `User.kt`、`HomeFeedItem.kt`、`Following.kt`。 |
| DATA-002 | `CookBookDB` Room 数据库骨架 | 预留给菜谱本地持久化；当前不是可用数据库。 | 仅 `abstract class CookBookDB : RoomDatabase()`，没有 `@Database`、entities、DAO、`Room.databaseBuilder` 或迁移；模块虽引入 Room runtime/ktx/compiler，但无实际建库入口。新增持久化前需先定义实体、DAO、版本与迁移策略，并确认产品允许的本地数据范围。 | `subModules/lib_cookbook/src/main/java/com/rhys/cookbook/room/CookBookDB.kt` | 全仓检索无 `Room.databaseBuilder`、`@Database`、`@Dao`、`@Entity` 或 `CookBookDB` 调用；`lib_cookbook/build.gradle.kts` 仅声明依赖与 KSP。 |

> 未发现项：`lib_network` 只有 4 个 bean，没有 Retrofit/OkHttp/接口定义/Repository；`lib_android_common` 的 `MMKVUtils` 是键值存储工具，见 UTIL-002，当前未初始化且无调用。异步基类见 ARCH-006；没有统一网络线程/重试封装。
