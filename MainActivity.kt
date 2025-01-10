package com.example.kidsdrawingapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.get
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi
import android.graphics.Color
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.OutputStream


class MainActivity : ComponentActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    val openGalleryLauncher:ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts
            .StartActivityForResult()){ result->
        if (result.resultCode == RESULT_OK && result.data != null){
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            imageBackground.setImageURI(result.data?.data)
        }
    }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts
            .RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val perMissionName = it.key
                val isGranted = it.value
                if (isGranted) {
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Permission granted now you can read the storage files.",
//                        Toast.LENGTH_LONG
//                    ).show()
                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (perMissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                        Toast.makeText(
                            this@MainActivity,
                            "Oops you just denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(10.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )

        val ibBrush: ImageButton = findViewById(R.id.ib_brush)
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibUndo: ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ibSave: ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            if (isReadStorageAllowed(this)){
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id
                        .fl_drawing_view_container)
                    //to save in android/data/kidsdrawingapp
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                    val imageData = getBitmapFromView(flDrawingView)
                    val fileName = "KidDrawingApp_${System.currentTimeMillis() / 1000}.jpg"
                    val savedFilePath = getSavedFilePath(fileName).toString()
                    //to save in the gallery
                    val isSaved = saveBitmapToGallery(this@MainActivity,
                        imageData, savedFilePath)
                    if (isSaved) {
                        Toast.makeText(
                            this@MainActivity,
                            "Image saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to save image. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val ibGallery: ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

        val ibShare: ImageButton = findViewById(R.id.ib_share)
        ibShare.setOnClickListener {
            val fileName = "KidDrawingApp_${System.currentTimeMillis() / 1000}.jpg"
            val savedFilePath = getSavedFilePath(fileName).toString()
            shareImage(savedFilePath)
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size :")

        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        })

        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        })

        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        })
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()

            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(ContextCompat
                .getDrawable(this,
                R.drawable.pallet_pressed))

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_normal
                )
            )
            mImageButtonCurrentPaint = view
        }
    }

//    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//    private fun isReadStorageAllowed(): Boolean {
//        val result = ContextCompat.checkSelfPermission(
//            this, Manifest.permission.READ_MEDIA_IMAGES
//        )
//        return result == PackageManager.PERMISSION_GRANTED
//    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestStoragePermission(){
        if (
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_MEDIA_IMAGES)
        ){
            showRationaleDialog("Kids Drawing App","Kids Drawing App " +
                    "needs to Access Your External Storage")
        }
        else {
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }


    private fun isReadStorageAllowed(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View): Bitmap {

        val returnedBitmap = Bitmap.createBitmap(view.width,
            view.height, Bitmap.Config.ARGB_8888)
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

    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String{
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {

                try {
                    val bytes = ByteArrayOutputStream()

                    mBitmap.compress(Bitmap.CompressFormat.PNG,
                        90, bytes)

                    val f = File(
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "KidDrawingApp_" +
                                System.currentTimeMillis() / 1000 + ".jpg"
                    )

                    val fo =
                        FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

//                    runOnUiThread {
//                        if (!result.isEmpty()) {
////                            Toast.makeText(
////                                this@MainActivity,
////                                "File saved successfully :$result",
////                                Toast.LENGTH_SHORT
////                            ).show()
////                            shareImage(result)
//                        } else {
////                            Toast.makeText(
////                                this@MainActivity,
////                                "Something went wrong while saving the file.",
////                                Toast.LENGTH_SHORT
////                            ).show()
//                        }
//                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/KidDrawingApp")
            }
        }

        // Insert the image metadata into MediaStore
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues)
        uri?.let {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG,
                        100, outputStream)
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }


    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(
            this@MainActivity, arrayOf(result),
            null) { path, uri ->
            val shareIntent = Intent(Intent.ACTION_SEND)
                .apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/jpeg"
            }
            startActivity(Intent.createChooser(shareIntent,
                "Share"))
        }
    }


    private fun getSavedFilePath(fileName: String): String? {
        val resolver = contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val filePath = it.getString(it.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATA))
                return filePath
            }
        }
        return null
    }
}



