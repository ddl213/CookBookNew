# 05 · 网络、存储与异步能力

> 本类盘点状态：已盘点。已核对 `lib_network`、`lib_cookbook` 的 Room 依赖与 `lib_android_common` 的存储工具。`P01-T02` 增量新增 Retrofit/OkHttp 认证客户端、认证 Repository、MMKV 会话存储与媒体 URL 解析；`P02-T01` 增量新增用户/好友接口、关系 DTO、分页、关系 Repository 与错误映射。因内容增长，本分类拆为两个子表。

**收录范围：**API/网络包装、Repository、Room/本地数据、缓存、协程/异步共用封装。

**索引原则：**记录权限、异常/重试、线程和数据安全边界，不擅自推断真实接口；骨架与未接线能力必须注明。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 数据模型、认证与存储](05-network-storage/01-foundation-auth.md) | 本地模型、Room 骨架、NetworkConfig、ApiClient、AuthApi/DTO、认证错误映射、AuthRepository、SessionStore | DATA-001～DATA-011 |
| [02 用户、好友关系与分页](05-network-storage/02-relation.md) | UserApi/FriendApi、关系 DTO、PageResponse、RelationErrorMapper、RelationApiCall/requireSuccessful、UserRepository/FriendRepository | DATA-012～DATA-021 |

## 覆盖与缺口

- 已覆盖 `lib_network` 当前全部接口、DTO、Repository、错误映射与会话存储源码；认证与关系链路共享 DATA-004 的 Retrofit/OkHttp 实例和鉴权拦截器。
- 菜谱、订单、聊天等后端接口尚未定义；没有统一重试、请求去重、离线缓存或乐观更新封装。Room 仍只有空骨架（DATA-002）。
- 好友关系、用户可见菜谱与预订资格均由后端判定；索引中的 `isFriend`、`visibility`、`canReserve` 只是展示字段，不能作为客户端授权依据。
