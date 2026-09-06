package com.example.assistive

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabsAdapter(
    private val tabs: List<BrowserTab>,
    private var activeTabIndex: Int,
    private val onTabClick: (Int) -> Unit,
    private val onCloseClick: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    fun updateActiveIndex(newIndex: Int) {
        val oldIndex = activeTabIndex
        activeTabIndex = newIndex
        if (oldIndex in tabs.indices) notifyItemChanged(oldIndex)
        if (newIndex in tabs.indices) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tab_item, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        val isSelected = (position == activeTabIndex)

        holder.txtTitle.text = if (tab.title.isNotBlank()) tab.title else "New Tab"
        holder.txtUrl.text = if (tab.url.isNotBlank()) tab.url else "about:blank"

        // Card border & background highlight for active tab
        holder.layoutRoot.setBackgroundResource(
            if (isSelected) R.drawable.bg_tab_card_selected else R.drawable.bg_tab_card
        )

        // Thumbnail preview
        val thumb = tab.thumbnail
        if (thumb != null && !thumb.isRecycled) {
            holder.imgPreview.setImageBitmap(thumb)
            holder.imgPreview.visibility = View.VISIBLE
            holder.layoutPlaceholder.visibility = View.GONE
        } else {
            holder.imgPreview.visibility = View.GONE
            holder.layoutPlaceholder.visibility = View.VISIBLE
            holder.txtPlaceholderUrl.text = getDomainName(tab.url)
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onTabClick(pos)
            }
        }

        holder.btnClose.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onCloseClick(pos)
            }
        }
    }

    override fun getItemCount(): Int = tabs.size

    private fun getDomainName(url: String): String {
        return try {
            val parsed = Uri.parse(url)
            val host = parsed.host
            if (!host.isNullOrBlank()) host else url
        } catch (e: Exception) {
            url
        }
    }

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutRoot: View = itemView.findViewById(R.id.layout_tab_card_root)
        val txtTitle: TextView = itemView.findViewById(R.id.txt_tab_card_title)
        val btnClose: ImageButton = itemView.findViewById(R.id.btn_tab_card_close)
        val imgPreview: ImageView = itemView.findViewById(R.id.img_tab_preview)
        val layoutPlaceholder: View = itemView.findViewById(R.id.layout_tab_placeholder)
        val txtPlaceholderUrl: TextView = itemView.findViewById(R.id.txt_tab_placeholder_url)
        val txtUrl: TextView = itemView.findViewById(R.id.txt_tab_card_url)
    }
}
