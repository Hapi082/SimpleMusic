package com.example.simplemusicplayer

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.simplemusicplayer.data.db.AppDatabase
import com.example.simplemusicplayer.data.repository.TrackRepository
import com.example.simplemusicplayer.data.repository.TrackRepositoryImpl
import com.example.simplemusicplayer.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var addButton: ImageButton

    private lateinit var adapter: TrackListAdapter
    private lateinit var repository: TrackRepository

    // ＋ボタンから起動するファイルピッカー
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                handlePickedAudio(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.rv_track_list)
        emptyView = findViewById(R.id.layout_empty_view)
        addButton = findViewById(R.id.btn_add_track)

        // Roomデータベース & Repository 初期化
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "simple_music_player.db"
        ).build()
        repository = TrackRepositoryImpl(db)

        adapter = TrackListAdapter(
            items = mutableListOf(),
            onItemClick = { track ->
                Log.d("MainActivity", "行タップ: ${track.title}")
                // 今後のStepで PlayerActivity へ遷移する処理を実装
            },
            onDeleteClick = { track ->
                Log.d("MainActivity", "削除タップ: ${track.title}")
                adapter.removeTrack(track)
                updateEmptyView()

                // DBからも削除
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.deleteTrack(track.id)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 起動時にDBからマイリストを読み込む
        lifecycleScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                repository.getAllTracks()
            }
            adapter.submitList(tracks)
            updateEmptyView()
        }

        // 追加ボタン：ファイルピッカー起動
        addButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("audio/mpeg", "audio/*"))
        }
    }

    private fun updateEmptyView() {
        val isEmpty = adapter.itemCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handlePickedAudio(uri: Uri) {
        val title = getDisplayNameFromUri(uri) ?: "Unknown Track"

        val track = Track(
            id = System.currentTimeMillis(), // 簡易ID（後で改善してもよい）
            title = title,
            uri = uri.toString(),
            addedAt = System.currentTimeMillis()
        )

        adapter.addTrack(track)
        updateEmptyView()

        // DBにも保存
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addTrack(track)
        }
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        // .mp3 など拡張子を取ってそれっぽくする
        return name?.substringBeforeLast(".")
    }
}
