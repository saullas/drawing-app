package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var selectedPaint : ImageButton? = null
    private var permissionsBeenGrantedBefore = false
    private var rootLayout : ConstraintLayout? = null

    var customProgressDialog : Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground : ImageView = findViewById(R.id.iv_background)

                imageBackground.setImageURI(result.data?.data)
            }
        }

    val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted && !permissionsBeenGrantedBefore) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_LONG).show()
                }

                permissionsBeenGrantedBefore = isGranted

                if (isGranted) {
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionName.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.cl_mainActivity)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setBrushSize(20.toFloat())

        val linLayoutColorPallete = findViewById<LinearLayout>(R.id.ll_colorPallete)

        selectedPaint = linLayoutColorPallete[1] as ImageButton
        selectedPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_selected)
        )

        val brushButton : ImageButton = findViewById(R.id.ib_brushSize)
        brushButton.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibGallery : ImageButton = findViewById(R.id.ib_loadBackgroundPhoto)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.undoDrawing()
        }

        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener {
            drawingView?.redoDrawing()
        }

        val ibClear : ImageButton = findViewById(R.id.ib_clear)
        ibClear.setOnClickListener {
            val builder : AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(title)
                .setMessage("Are you sure you want to clear the canvas?")
                .setPositiveButton("Yes") {
                        dialog, _ ->
                    drawingView?.clearDrawing()
                    dialog.dismiss()
                }
                .setNegativeButton("No") {
                    dialog, _ -> dialog.dismiss()
                }
            builder.create().show()
        }

        val ibSave : ImageButton = findViewById(R.id.ib_saveDrawing)
        ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                customProgressDialog = Dialog(this)
                customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
                customProgressDialog?.show()
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.fl_drawingViewContainer)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallButton = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallButton.setOnClickListener {
            drawingView?.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumButton = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumButton.setOnClickListener {
            drawingView?.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeButton = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeButton.setOnClickListener {
            drawingView?.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun onPaintSelected(view: View) {
        if (view != selectedPaint) {
            val imageButton = view as ImageButton // we know that only color buttons can call this function
            val colorHash = imageButton.tag.toString()
            drawingView?.setColor(colorHash)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_selected)
            )
            selectedPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            selectedPaint = view
        }
    }

    private fun isReadStorageAllowed() : Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationaleDialog("Drawing App", "Drawing App needs access to your external storage to be able to load images from your phone")
        } else {
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun showRationaleDialog(title: String, message: String) {
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") {
                dialog, _ -> dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background

        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(bitmap: Bitmap?) : String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val file = File(externalCacheDir?.absoluteFile.toString() +
                        File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".png"
                    )

                    val fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(bytes.toByteArray())
                    fileOutputStream.close()

                    result = file.absolutePath

                    runOnUiThread {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        if (result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File saved successfuly at: $result", Toast.LENGTH_SHORT).show()
//                            Snackbar.make(rootLayout!!.rootView, "Image saved successfuly at: $result", Snackbar.LENGTH_LONG)
//                                .setAction("Share", View.OnClickListener {
//
//                                })
//                                .show()
                            shareImage(result)
                        } else {
                            Toast.makeText(this@MainActivity, "Something went wrong when saving the file", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) {
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }
}















