package com.bili.tv.bili_tv_app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.data.model.Video
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

/**
 * 视频网格适配器 - 支持触摸和遥控器
 */
class VideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videos = mutableListOf<Video>()

    fun getVideoAt(position: Int): Video? = videos.getOrNull(position)

    fun setVideos(newVideos: List<Video>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
    }

    fun addVideos(newVideos: List<Video>) {
        val startPosition = videos.size
        videos.addAll(newVideos)
        notifyItemRangeInserted(startPosition, newVideos.size)
    }

    fun clearVideos() {
        val size = videos.size
        videos.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_card, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount() = videos.size

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val title: TextView = itemView.findViewById(R.id.videoTitle)
        private val author: TextView = itemView.findViewById(R.id.videoAuthor)
        private val playCount: TextView = itemView.findViewById(R.id.videoPlayCount)
        private val duration: TextView = itemView.findViewById(R.id.videoDuration)

        init {
            itemView.isFocusable = true
            itemView.isClickable = true

            // 点击事件
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onVideoClick(videos[position])
                }
            }

            // 触摸点击支持
            itemView.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    itemView.performClick()
                }
                true
            }
        }

        fun bind(video: Video) {
            title.text = video.title
            author.text = video.ownerName
            playCount.text = video.viewCount
            duration.text = video.durationStr

            // 加载封面图
            Glide.with(itemView.context)
                .load(video.pic)
                .placeholder(R.drawable.placeholder_video)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(thumbnail)
        }
    }
}