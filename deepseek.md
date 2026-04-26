以下是从第二个可正常显示列表的项目中，与**视频列表显示**直接相关的全部核心文件，按职能分组：

---

### 1. 数据模型（列表每一项的数据结构）
- `core/model/VideoCard.kt` — 视频卡片数据（所有列表的通用模型）
- `core/model/Bangumi.kt` — 番剧/影视项
- `core/model/Live.kt` — 直播房间项
- `core/model/Following.kt` — 关注用户项
- `core/model/FavFolder.kt` — 收藏夹项

### 2. 网络请求、签名与 Cookie（数据来源的核心）
- `core/net/BiliClient.kt` — OkHttp 客户端、通用 WBI 签名 URL 生成
- `core/net/WbiSigner.kt` — 正确 WBI 签名算法
- `core/net/CookieStore.kt` — Cookie 持久化存储（含 `buvid3` 等设备指纹）
- `core/net/WebCookieMaintainer.kt` — 自动维护 Cookie（登录、风控票据）
- `core/api/BiliApi.kt` — 所有 API 的聚合入口
- `core/api/VideoApi.kt` — 视频相关 API（推荐、热门、分区、详情等）
- `core/api/SearchApi.kt` — 搜索 API

### 3. 列表分页与导航基础设施
- `core/paging/PagedGridStateMachine.kt` — 分页状态机（统一管理加载中、无更多数据等状态）
- `core/ui/DpadGridController.kt` — DPAD 方向键导航（上下左右处理、加载更多触发）
- `core/ui/GridSpanPolicy.kt` — 网格列数策略

### 4. 核心列表适配器（RecyclerView.Adapter）
- `feature/video/VideoCardAdapter.kt` — **最重要的视频卡片适配器**，几乎所有视频列表都用它
- `feature/my/BangumiFollowAdapter.kt` — 追番/追剧卡片适配器
- `feature/live/LiveRoomAdapter.kt` — 直播房间卡片适配器
- `feature/live/LiveAreaAdapter.kt` — 直播分区适配器
- `feature/following/FollowingGridAdapter.kt` — 关注用户网格适配器
- `feature/dynamic/FollowingAdapter.kt` — 动态页左侧关注列表适配器
- `feature/my/FavFolderAdapter.kt` — 收藏夹适配器
- `feature/search/SearchKeyAdapter.kt` / `SearchSuggestAdapter.kt` / `SearchHotAdapter.kt` — 搜索建议、热门搜索适配器

### 5. 各种列表展示的 Fragment / Activity
- `feature/video/VideoGridFragment.kt` — **通用视频网格**（推荐、热门、分区都用这一个 Fragment）
- `feature/home/HomeFragment.kt` — 首页（ViewPager2 + `VideoGridFragment` / `PgcRecommendGridFragment`）
- `feature/home/PgcRecommendGridFragment.kt` — 番剧/影视推荐网格
- `feature/dynamic/DynamicFragment.kt` — 动态页（关注列表 + 视频网格）
- `feature/live/LiveGridFragment.kt` — 直播推荐列表
- `feature/live/LiveAreaDetailFragment.kt` — 直播分区内房间列表
- `feature/search/SearchFragment.kt` — 搜索结果列表
- `feature/my/MyHistoryFragment.kt` — 历史记录列表
- `feature/my/MyToViewFragment.kt` — 稍后再看列表
- `feature/my/MyLikeFragment.kt` — 点赞列表
- `feature/my/MyFavFolderDetailFragment.kt` — 收藏夹内视频列表
- `feature/my/MyBangumiFollowFragment.kt` — 追番/追剧列表
- `feature/following/FollowingListActivity.kt` — 关注用户列表
- `feature/video/VideoDetailActivity.kt` — 视频详情页（底部推荐列表 + 分P/合集列表）
- `feature/video/RegionDetailActivity.kt` — 分区详情页
- `feature/tag/TagDetailActivity.kt` — 标签详情页
- `feature/following/UpDetailActivity.kt` — UP 主主页视频列表
- `feature/custom/CustomDynamicVideoFragment.kt` — 自定义页动态视频列表

### 6. 关键布局文件（list item）
- `layout/item_video_card.xml` — 视频卡片布局
- `layout/fragment_video_grid.xml` — 通用视频网格布局（SwipeRefresh + RecyclerView）
- `layout/fragment_home.xml` — 首页布局
- `layout/fragment_live_grid.xml` — 直播网格布局
- `layout/fragment_search.xml` — 搜索页布局
- `layout/item_bangumi_follow.xml` — 番剧卡片
- `layout/item_live_card.xml` — 直播卡片
- `layout/item_following_grid.xml` — 关注用户卡片
- `layout/item_fav_folder.xml` — 收藏夹卡片
- `layout/item_search_hot.xml` / `item_search_key.xml` 等

### 7. 辅助工具类（焦点、刷新、滚动）
- `core/ui/RefreshFocus.kt` — 刷新后聚焦第一项
- `core/ui/RecyclerViewFocus.kt` — 可靠的 RecyclerView 焦点请求
- `core/ui/RecyclerViewSmoothScroll.kt` — 平滑滚动到指定位置
- `core/ui/ViewTasks.kt` — 安全的延迟执行任务

---

**关键点**：对比两个项目，第一个项目缺失的最重要部分就是 **WBI 签名、Cookie 存储、以及完整的分页状态机**。另一个 AI 研究时，重点看 `core/net/WbiSigner.kt`、`core/net/CookieStore.kt` 和 `core/paging/PagedGridStateMachine.kt` 这三个文件即可找到根本差异。