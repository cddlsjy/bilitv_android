package com.bili.tv.bili_tv_app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bili.tv.bili_tv_app.MainActivity
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.data.api.BilibiliApi
import com.bili.tv.bili_tv_app.data.model.Video
import com.bili.tv.bili_tv_app.ui.home.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 搜索页面 - 支持触摸和遥控器
 */
class SearchFragment : Fragment() {

    private lateinit var searchInput: EditText
    private lateinit var searchResults: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: View
    private lateinit var hintView: View

    private lateinit var videoAdapter: VideoAdapter

    private var searchJob: Job? = null
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupSearchInput()
        setupResultsList()
    }

    private fun initViews(view: View) {
        searchInput = view.findViewById(R.id.searchInput)
        searchResults = view.findViewById(R.id.searchResults)
        loadingView = view.findViewById(R.id.loadingView)
        emptyView = view.findViewById(R.id.emptyView)
        hintView = view.findViewById(R.id.hintView)
    }

    private fun setupSearchInput() {
        // 软键盘搜索按钮
        searchInput.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(textView.text.toString())
                true
            } else {
                false
            }
        }

        // 触摸点击键盘搜索按钮
        searchInput.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                // 显示键盘
                searchInput.requestFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            false
        }
    }

    private fun setupResultsList() {
        videoAdapter = VideoAdapter { video ->
            (activity as? MainActivity)?.navigateToPlayer(video.bvid)
        }

        searchResults.layoutManager = GridLayoutManager(context, 4)
        searchResults.adapter = videoAdapter

        // 触摸点击支持
        searchResults.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val child = searchResults.findChildViewUnder(event.x, event.y)
                child?.let {
                    val position = searchResults.getChildAdapterPosition(child)
                    if (position >= 0 && position < videoAdapter.itemCount) {
                        val video = videoAdapter.getVideoAt(position)
                        video?.let { (activity as? MainActivity)?.navigateToPlayer(it.bvid) }
                    }
                }
            }
            false
        }
    }

    private fun performSearch(keyword: String) {
        if (keyword.isBlank()) {
            Toast.makeText(context, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300) // 防抖
            searchVideos(keyword)
        }
    }

    private fun searchVideos(keyword: String) {
        if (isLoading) return
        isLoading = true

        showLoading(true)
        hideHint()

        lifecycleScope.launch {
            val videos = try {
                BilibiliApi.searchVideos(keyword)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            isLoading = false
            showLoading(false)

            if (videos.isEmpty()) {
                showEmpty(true)
            } else {
                showEmpty(false)
                videoAdapter.setVideos(videos)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        searchResults.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun hideHint() {
        hintView.visibility = View.GONE
    }
}