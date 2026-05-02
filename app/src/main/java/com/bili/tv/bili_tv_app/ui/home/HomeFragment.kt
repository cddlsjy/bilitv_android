package com.bili.tv.bili_tv_app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bili.tv.bili_tv_app.MainActivity
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.data.api.BilibiliApi
import com.bili.tv.bili_tv_app.data.model.Video
import kotlinx.coroutines.launch

/**
 * 首页Fragment - 支持触摸和遥控器导航
 */
class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var categoryTabs: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: View

    private lateinit var videoAdapter: VideoAdapter

    private var currentCategory = 0
    private val categories = listOf("推荐", "热门", "动画", "音乐", "游戏", "科技", "知识")
    // 分区 ID: 0=推荐，1=热门 (特殊处理), 其他=分区 ID
    private val categoryTids = listOf(-1, -1, 1, 3, 4, 188, 36)

    private var isLoading = false
    private var currentPage = 1
    private var hasMoreData = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupCategoryTabs()
        setupVideoGrid()
        setupRefreshLayout()

        // 初始加载
        loadVideos()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.videoRecyclerView)
        categoryTabs = view.findViewById(R.id.categoryTabs)
        loadingView = view.findViewById(R.id.loadingView)
        emptyView = view.findViewById(R.id.emptyView)
    }

    private fun setupCategoryTabs() {
        val tabAdapter = CategoryTabAdapter(categories) { index ->
            if (currentCategory != index) {
                currentCategory = index
                currentPage = 1
                hasMoreData = true
                videoAdapter.clearVideos()
                loadVideos()
            }
        }
        categoryTabs.layoutManager = LinearLayoutManager(
            context, LinearLayoutManager.HORIZONTAL, false
        )
        categoryTabs.adapter = tabAdapter
    }

    private fun setupVideoGrid() {
        videoAdapter = VideoAdapter { video ->
            // 点击视频进入播放页面
            (activity as? MainActivity)?.navigateToPlayer(video.bvid)
        }

        // 4列网格布局
        val layoutManager = GridLayoutManager(context, 4)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return 1
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = videoAdapter

        // 添加滚动监听，加载更多
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && !isLoading && hasMoreData) {
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if (lastVisibleItem >= totalItemCount - 4) {
                        loadMoreVideos()
                    }
                }
            }
        })
    }

    private fun setupRefreshLayout() {
        // 下拉刷新支持 - 通过长按手势触发
        recyclerView.setOnLongClickListener {
            currentPage = 1
            hasMoreData = true
            loadVideos()
            true
        }
    }

    private fun loadVideos() {
        if (isLoading) return
        isLoading = true

        showLoading(true)

        lifecycleScope.launch {
            val videos = try {
                when (currentCategory) {
                    0 -> {
                        // 推荐
                        android.util.Log.d("HomeFragment", "Loading recommendation...")
                        BilibiliApi.getRecommendVideos()
                    }
                    1 -> {
                        // 热门
                        android.util.Log.d("HomeFragment", "Loading popular videos, page=$currentPage")
                        BilibiliApi.getPopularVideos(currentPage)
                    }
                    else -> {
                        // 分区
                        val tid = categoryTids[currentCategory]
                        android.util.Log.d("HomeFragment", "Loading region $tid, page=$currentPage")
                        BilibiliApi.getRegionVideos(tid, currentPage)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Load videos error: ${e.message}", e)
                e.printStackTrace()
                emptyList()
            }

            isLoading = false
            showLoading(false)
            
            android.util.Log.d("HomeFragment", "Loaded ${videos.size} videos")

            if (videos.isEmpty()) {
                showEmpty(true)
            } else {
                showEmpty(false)
                videoAdapter.setVideos(videos)
            }
        }
    }

    private fun loadMoreVideos() {
        if (isLoading || !hasMoreData) return
        isLoading = true
        currentPage++

        lifecycleScope.launch {
            val videos = try {
                when (currentCategory) {
                    0 -> BilibiliApi.getRecommendVideos()
                    1 -> BilibiliApi.getPopularVideos(currentPage)
                    else -> BilibiliApi.getRegionVideos(categoryTids[currentCategory], currentPage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            isLoading = false

            if (videos.isEmpty()) {
                hasMoreData = false
            } else {
                videoAdapter.addVideos(videos)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
    }
}

/**
 * 分类标签适配器
 */
class CategoryTabAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (Int) -> Unit
) : RecyclerView.Adapter<CategoryTabAdapter.ViewHolder>() {

    private var selectedIndex = 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.tabText)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position != selectedIndex) {
                    val oldIndex = selectedIndex
                    selectedIndex = position
                    notifyItemChanged(oldIndex)
                    notifyItemChanged(selectedIndex)
                    onCategorySelected(position)
                }
            }

            // 支持触摸点击
            itemView.isFocusable = true
            itemView.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    itemView.performClick()
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = categories[position]
        holder.textView.isSelected = position == selectedIndex

        // 设置焦点样式
        holder.itemView.isSelected = position == selectedIndex
    }

    override fun getItemCount() = categories.size
}
