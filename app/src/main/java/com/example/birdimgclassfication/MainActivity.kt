package com.example.birdimgclassfication

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation.ActivityResult
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Gallery
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.birdimgclassfication.databinding.ActivityMainBinding
import com.example.birdimgclassfication.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding :ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput : TextView
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView
        button = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED){
                takePicturePreview.launch(null)
            }
            else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        buttonLoad.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg","image/png","image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            }else{
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        tvOutput.setOnClickListener{
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutput.text}"))
            startActivity(intent)
        }

        imageView.setOnLongClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted ->
        if(granted){
            takePicturePreview.launch(null)
        }else{
            Toast.makeText(this,"Permission Denied !! Try Again",Toast.LENGTH_SHORT).show()
        }
    }



    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){ bitmap ->
        if(bitmap != null){
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }


    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        Log.i("TAG","This is the result : ${result.data} ${result.resultCode}")
        onResultRecieved(GALLERY_REQUEST_CODE,result)
    }

    private fun onResultRecieved(requestCode: Int,result : androidx.activity.result.ActivityResult?){
        when(requestCode){
            GALLERY_REQUEST_CODE -> {
                if(result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let{uri ->
                        Log.i("TAG","onResultRecieved: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                }else{
                    Log.i("TAG","onActivityResult: error in selecting Image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        val birdsModel = BirdsModel.newInstance(this)

    // Creates inputs for reference.
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888,true)
        val tfimage = TensorImage.fromBitmap(newBitmap)

    // Runs model inference and gets result.
        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply{
                sortByDescending { it.score }
            }
        val highProbabilityOutput = outputs[0]

        tvOutput.text = highProbabilityOutput.label
        Log.i("TAG","output Generator: $highProbabilityOutput")

    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        isGranted: Boolean ->
        if(isGranted){
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Download Image?")
                .setMessage("Do you want to download this image to your device?")
                .setPositiveButton("Yes"){_,_->
                    val drawable : BitmapDrawable = imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("No"){dialog,_ ->
                    dialog.dismiss()
                }
                .show()
        }else{
            Toast.makeText(this,"Please allow permission to download image",Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadImage(mBitmap: Bitmap):Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Birds Images"+System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if(uri != null){
            contentResolver.insert(uri,contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if(!mBitmap.compress(Bitmap.CompressFormat.PNG,100,outputStream)){
                        throw IOException("Couldn't Save Bitmap")
                    }
                    else{
                        Toast.makeText(applicationContext,"Image Saved",Toast.LENGTH_SHORT).show()
                    }
                }
                return it
            }
        }
        return null
    }
}