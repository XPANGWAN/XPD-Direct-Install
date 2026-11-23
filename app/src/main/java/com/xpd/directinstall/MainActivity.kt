package com.xpd.directinstall
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {
 private val silver=Color.rgb(205,210,218); private val muted=Color.rgb(145,151,160)
 private val panel=Color.rgb(27,29,34); private val bg=Color.rgb(12,13,16)
 private val files=mutableListOf<PkgEntry>(); private val served=ConcurrentHashMap<String,Long>()
 private var server:PkgHttpServer?=null; private lateinit var ip:EditText; private lateinit var port:EditText
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:LinearLayout
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var clear:Button; private lateinit var serverInfo:TextView
 private var busy=false
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi();restoreState()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(20,22,20,20);setBackgroundColor(bg)}
  val scroll=ScrollView(this).apply{clipToPadding=false;isFillViewport=true}
  val body=LinearLayout(this).apply{orientation=1}
  val brand=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val mark=ImageView(this).apply{setImageResource(R.drawable.xpd_wolf_mark);scaleType=ImageView.ScaleType.FIT_CENTER}
  brand.addView(mark,LinearLayout.LayoutParams(dp(66),dp(66)).apply{rightMargin=dp(14)})
  val brandText=LinearLayout(this).apply{orientation=1}
  brandText.addView(label("XPD",30f,Color.WHITE).apply{typeface=android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD);letterSpacing=.10f})
  brandText.addView(label("DIRECT INSTALL",13f,silver).apply{letterSpacing=.22f})
  brandText.addView(label("ANDROID PKG SENDER  •  v0.7.0",10f,muted).apply{letterSpacing=.08f;setPadding(1,5,0,0)})
  brand.addView(brandText,LinearLayout.LayoutParams(0,-2,1f))
  body.addView(brand,lp(-1,dp(82)).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("01  /  CONSOLE"))
  val connection=card()
  connection.addView(label("PLAYSTATION 4  •  LOCAL NETWORK",10f,muted).apply{letterSpacing=.10f},lp(-1,22))
  ip=field("192.168.1.XX",false).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}
  connection.addView(label("CONSOLE IP ADDRESS",10f,muted),lp(-1,20));connection.addView(ip,lp(-1,52).apply{bottomMargin=12})
  port=field("12800",true).apply{setText("12800")};connection.addView(label("RECEIVER PORT",10f,muted),lp(-1,20));connection.addView(port,lp(-1,52))
  check=button("CHECK CONNECTION"){checkReceiver()};connection.addView(check,lp(-1,50).apply{topMargin=14})
  connection.addView(label("RPI: 12800  /  GoldHEN status: 9090 (detection only)",10f,muted).apply{setPadding(2,12,2,0)})
  body.addView(connection,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("02  /  YOUR PACKAGES"))
  val packages=card()
  val queueTop=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  queueTop.addView(label("TRANSFER QUEUE",10f,silver).apply{letterSpacing=.12f},LinearLayout.LayoutParams(0,dp(24),1f))
  clear=button("CLEAR"){if(!busy){files.clear();served.clear();refreshQueue();saveState()}}.apply{isEnabled=!busy};queueTop.addView(clear,lp(dp(82),dp(34)))
  packages.addView(queueTop)
  packages.addView(button("＋   ADD PKG FILES",true){chooseFiles()},lp(-1,dp(52)).apply{topMargin=dp(10)})
  fileInfo=label("No packages selected",12f,muted).apply{setPadding(2,dp(14),2,dp(8))};packages.addView(fileInfo)
  queue=LinearLayout(this).apply{orientation=1};queue.addView(label("Choose one or more PKG files to build your queue.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))});packages.addView(queue)
  body.addView(packages,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("03  /  SEND TO CONSOLE"))
  val transfer=card()
  status=label("READY  •  Select PKG files to begin.",12f,silver).apply{setPadding(2,dp(4),2,dp(14))};transfer.addView(status)
  send=button("START TRANSFER",true){sendFiles()}.apply{isEnabled=false};transfer.addView(send,lp(-1,dp(56)))
  serverInfo=label("Local phone server  •  Port 8080",10f,muted).apply{setPadding(2,dp(12),2,2)};transfer.addView(serverInfo)
  body.addView(transfer,lp(-1,-2).apply{bottomMargin=dp(18)})
  body.addView(label("Both devices must be connected to the same Wi-Fi network. Keep XPD open while the PS4 downloads the selected files. A successful request means accepted by the receiver, not confirmed installation.",10f,muted).apply{setPadding(4,4,4,22);gravity=Gravity.CENTER})
  scroll.addView(body);root.addView(scroll);setContentView(root)
 }
 private fun prefs()=getSharedPreferences("xpd_direct_install",MODE_PRIVATE)
 private fun restoreState(){
  val savedIp=prefs().getString("console_ip","")?:""
  if(savedIp.isNotEmpty())ip.setText(savedIp)
  val savedPort=prefs().getString("console_port","12800")?:"12800"
  port.setText(savedPort)
  val raw=prefs().getString("pkg_uris","[]")?:"[]"
  try{
   val arr=JSONArray(raw)
   for(i in 0 until arr.length()){
    val u=Uri.parse(arr.getString(i))
    if(files.any{it.uri==u})continue
    try{
     val name=query(u,OpenableColumns.DISPLAY_NAME)?: "package.pkg"
     if(isValidPkg(u)){
      val size=packageSize(u)
      val metadata=PkgMetadataReader.read(contentResolver,u)
      files.add(PkgEntry(files.size.toString(),u,displayPkgName(name),size,metadata))
     }
    }catch(_:Exception){}
   }
  }catch(_:Exception){}
  refreshQueue()
 }
 private fun saveState(){
  if(!::ip.isInitialized||!::port.isInitialized)return
  val arr=JSONArray()
  files.forEach{arr.put(it.uri.toString())}
  prefs().edit().putString("console_ip",ip.text.toString().trim())
   .putString("console_port",port.text.toString().trim())
   .putString("pkg_uris",arr.toString()).apply()
 }
 override fun onPause(){saveState();super.onPause()}
 private fun sectionHeader(s:String)=label(s,10f,silver).apply{letterSpacing=.14f;setPadding(2,0,2,10)}
 private fun card()=LinearLayout(this).apply{
  orientation=1;setPadding(16,16,16,16)
  background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=18f;setStroke(1,Color.rgb(48,51,58))}
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
 private fun chooseFiles(){if(busy)return;val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull()?.let { n -> n in 0..255 } == true}
 private fun checkReceiver(){
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid port.";return}
  check.isEnabled=false;status.text="Checking PS4 receiver…"
  Thread{
   val result=if(p==9090){
    probeHttp(host,9090,"/status","GoldHEN")
   }else{
    probeHttp(host,p,"/api","RPI")
   }
   runOnUiThread{status.text=result;check.isEnabled=true}
  }.start()
 }
 private fun probeHttp(host:String,p:Int,path:String,kind:String):String{
  var conn:HttpURLConnection?=null
  return try{
   conn=URL("http://$host:$p$path").openConnection() as HttpURLConnection
   conn.requestMethod="GET";conn.connectTimeout=4000;conn.readTimeout=4000
   val code=conn.responseCode
   val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(kind=="GoldHEN"&&body.contains("status")&&body.contains("ready"))
    "GoldHEN detected on 9090. Online; this build does not yet include the GoldHEN installer payload."
   else if(kind=="RPI"&&(body.contains("Unsupported method",true)&&body.contains("fail",true)))
    "RPI detected on port $p."
   else "Port $p responded (HTTP $code), but $kind was not confirmed."
  }catch(e:Exception){"No $kind response on $p: "+(e.message?:"connection failed")}finally{conn?.disconnect()}
 }
 private fun sendFiles(){
  if(busy)return;if(files.isEmpty()){status.text="Add at least one .pkg file.";return}
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return};if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  if(p==9090){status.text="GoldHEN uses payload injection, not the RPI /api/install request. This build detects GoldHEN but does not yet send PKG through it.";return}
  if(p!=12800){status.text="RPI installation is supported on port 12800 in this build.";return}
  val local=localIp()?:run{status.text="Connect phone and PS4 to the same Wi-Fi network.";return}
  val s=PkgHttpServer(contentResolver,8080,files.toList(),onBytes={id,n->served.compute(id){_,old->((old?:0L)+n).let{v->val size=files.firstOrNull{it.id==id}?.size?:-1L;if(size>0)v.coerceAtMost(size)else v}};runOnUiThread{refreshQueue()}},onServerError={m->runOnUiThread{status.text=m}})
  server?.stop();server=null
  if(!s.start()){status.text="Could not start local server on port 8080.";return}
  server=s;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Phone server: http://$local:8080/";busy=true;send.isEnabled=false;status.text="Submitting package queue to PS4…"
  Thread{var c:HttpURLConnection?=null;val msg=try{
   val links=JSONArray();files.forEach{links.put("http://$local:8080/pkg/${it.id}")}
   val json=JSONObject().put("type","direct").put("packages",links)
   c=URL("http://$host:$p/api/install").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
   c.outputStream.use{it.write(json.toString().toByteArray(Charsets.UTF_8))}
   val code=c.responseCode;val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(code in 200..299&&body.contains("success",true))"RPI accepted the package request. Confirm the download and installation on the PS4.\n$body" else "RPI did not confirm acceptance (HTTP $code).\n$body"
  }catch(e:Exception){"Could not submit packages: "+(e.message?:"network error")}finally{c?.disconnect()}
  runOnUiThread{status.text=msg;busy=false;send.isEnabled=files.isNotEmpty()}
  }.start()
 }
 private fun localIp():String? {
  return try { val all=NetworkInterface.getNetworkInterfaces() ?: return null; for(n in Collections.list(all)) if(n.isUp && !n.isLoopback) for(a in Collections.list(n.inetAddresses)) if(a is Inet4Address && a.isSiteLocalAddress && !a.isLoopbackAddress) return a.hostAddress; null } catch(_:Exception) { null }
 }
 private fun refreshQueue(){
  fileInfo.text=if(files.isEmpty())"No packages selected" else "${files.size} package(s) selected"
  queue.removeAllViews()
  if(files.isEmpty()){queue.addView(label("Your selected packages will appear here.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))})}
  files.forEachIndexed{i,f->
   val row=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(10),dp(12),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(19,21,25));cornerRadius=dp(12).toFloat();setStroke(1,Color.rgb(45,48,55))}}
   val meta=f.metadata
   val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
   if(meta?.iconPng!=null){
    try{val bitmap=android.graphics.BitmapFactory.decodeByteArray(meta.iconPng,0,meta.iconPng.size);if(bitmap!=null){val cover=ImageView(this@MainActivity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP};top.addView(cover,lp(dp(58),dp(58)).apply{rightMargin=dp(12)})}}catch(_:Exception){}
   }
   val textCol=LinearLayout(this).apply{orientation=1}
   textCol.addView(label(meta?.displayTitle?.takeIf{it.isNotBlank()}?:f.name,13f,silver).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
   val subtitle=listOfNotNull(meta?.platform,meta?.titleId?.takeIf{it.isNotBlank()},meta?.version?.takeIf{it.isNotBlank()}).joinToString("  •  ")
   if(subtitle.isNotBlank())textCol.addView(label(subtitle,10f,muted).apply{setPadding(0,dp(4),0,0)})
   top.addView(textCol,LinearLayout.LayoutParams(0,-2,1f));row.addView(top)
   val n=served[f.id]?:0L;val pct=if(f.size>0)(n*100/f.size).coerceIn(0,100)else 0
   row.addView(label("${f.name}  •  ${sizeText(f.size)}  •  sent ${pct}%",10f,muted).apply{setPadding(0,dp(7),0,0);maxLines=2})
   queue.addView(row,lp(-1,-2).apply{topMargin=dp(7)})
  }
  send.isEnabled=files.isNotEmpty()&&!busy;clear.isEnabled=!busy
 }
 private fun sizeText(n:Long):String{if(n<0)return "Size unknown";val mb=n/1048576.0;return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024)else String.format(Locale.US,"%.1f MB",mb)}
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=41||resultCode!=RESULT_OK||data==null)return
  val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)};if(uris.isEmpty())data.data?.let{uris.add(it)}
  var count=0;var rejected=0;uris.forEach{u->if(files.any{it.uri==u})return@forEach
   try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){}
   if(!isValidPkg(u)){rejected++;return@forEach}
   val name=query(u,OpenableColumns.DISPLAY_NAME)?:"package.pkg"
   val metadata=PkgMetadataReader.read(contentResolver,u)
   files.add(PkgEntry(files.size.toString(),u,displayPkgName(name),packageSize(u),metadata));count++}
  status.text=when{count>0&&rejected>0->"$count valid PKG file(s) added; $rejected invalid file(s) skipped.";count>0->"$count valid PKG file(s) added.";rejected>0->"No valid PlayStation PKG found. Check the selected file.";else->"No new PKG files selected."};refreshQueue();saveState()
 }
 private fun displayPkgName(name:String)=if(name.lowercase(Locale.ROOT).endsWith(".pkg"))name else name+".pkg"
 private fun isValidPkg(u:Uri):Boolean = PkgMetadataReader.hasPkgMagic(contentResolver,u)
 private fun packageSize(u:Uri):Long { val column=query(u,OpenableColumns.SIZE)?.toLongOrNull(); if(column!=null&&column>=0)return column; return try{contentResolver.openFileDescriptor(u,"r")?.use{it.statSize}?:-1L}catch(_:Exception){-1L} }
 private fun query(u:Uri,col:String):String?{var c:Cursor?=null;return try{c=contentResolver.query(u,arrayOf(col),null,null,null);if(c!=null&&c.moveToFirst()){val i=c.getColumnIndex(col);if(i>=0&&!c.isNull(i))c.getString(i)else null}else null}catch(_:Exception){null}finally{c?.close()}}
 override fun onDestroy(){server?.stop();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy()}
}
