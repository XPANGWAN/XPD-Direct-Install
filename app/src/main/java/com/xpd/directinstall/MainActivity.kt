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
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MainActivity : Activity() {
 private val silver=Color.rgb(205,210,218); private val muted=Color.rgb(145,151,160)
 private val panel=Color.rgb(27,29,34); private val bg=Color.rgb(12,13,16)
 private val files=mutableListOf<PkgEntry>(); private val served=ConcurrentHashMap<String,Long>(); private val progressViews=ConcurrentHashMap<String,ProgressBar>(); private val progressLabels=ConcurrentHashMap<String,TextView>()
 private var server:PkgHttpServer?=null; private lateinit var ip:EditText; private lateinit var port:EditText
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:LinearLayout; private lateinit var queueSearch:EditText
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var clear:Button; private lateinit var stopServerButton:Button; private lateinit var pauseButton:Button; private lateinit var retryButton:Button; private lateinit var serverInfo:TextView
 @Volatile private var busy=false; @Volatile private var scanning=false; @Volatile private var transferCancelled=false; @Volatile private var paused=false; private val retryEntries=mutableListOf<PkgEntry>(); private var filterRole="ALL"
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi();server=TransferRuntime.server;stopServerButton.isEnabled=server!=null;if(server!=null)serverInfo.text="Local file server active on port 9898.";restoreState()}
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
  brandText.addView(label("ANDROID PKG SENDER  •  v0.9.1",10f,muted).apply{letterSpacing=.08f;setPadding(1,5,0,0)})
  brand.addView(brandText,LinearLayout.LayoutParams(0,-2,1f))
  body.addView(brand,lp(-1,dp(82)).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("01  /  CONSOLE"))
  val connection=card()
  connection.addView(label("PLAYSTATION CONSOLE  •  LOCAL NETWORK",10f,muted).apply{letterSpacing=.10f},lp(-1,22))
  ip=field("192.168.1.XX",false).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}
  connection.addView(label("CONSOLE IP ADDRESS",10f,muted),lp(-1,20));connection.addView(ip,lp(-1,52).apply{bottomMargin=12})
  port=field("12800",true).apply{setText("12800")};connection.addView(label("RECEIVER PORT",10f,muted),lp(-1,20));connection.addView(port,lp(-1,52))
  connection.addView(button("FIND CONSOLE ON WI-FI"){discoverConsole()},lp(-1,48).apply{topMargin=12})
  check=button("CHECK CONNECTION"){checkReceiver()};connection.addView(check,lp(-1,50).apply{topMargin=8})
  connection.addView(label("RPI: 12800  /  GoldHEN installer: 9090, 9021 or 9020",10f,muted).apply{setPadding(2,12,2,0)})
  body.addView(connection,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("02  /  YOUR PACKAGES"))
  val packages=card()
  val queueTop=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  queueTop.addView(label("TRANSFER QUEUE",10f,silver).apply{letterSpacing=.12f},LinearLayout.LayoutParams(0,dp(24),1f))
  clear=button("CLEAR"){if(!busy){files.clear();served.clear();refreshQueue();saveState()}}.apply{isEnabled=!busy};queueTop.addView(clear,lp(dp(82),dp(34)))
  packages.addView(queueTop)
  packages.addView(button("＋   ADD PKG FILES",true){chooseFiles()},lp(-1,dp(52)).apply{topMargin=dp(10)})
  packages.addView(button("SCAN PKG FOLDER"){chooseFolder()},lp(-1,dp(46)).apply{topMargin=dp(8)})
  fileInfo=label("No packages selected",12f,muted).apply{setPadding(2,dp(14),2,dp(8))};packages.addView(fileInfo)
  queueSearch=field("Search title, Title ID or file name",false).apply{addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshQueue()}
   override fun afterTextChanged(s:android.text.Editable?){}
  })}
  packages.addView(queueSearch,lp(-1,dp(44)).apply{topMargin=dp(6)})
  val filterRow=LinearLayout(this).apply{orientation=0}
  listOf("ALL","BASE","UPDATE","DLC").forEach{tag->
   filterRow.addView(button(tag,tag=="ALL"){filterRole=tag;refreshQueue()},LinearLayout.LayoutParams(0,dp(34),1f).apply{rightMargin=dp(4);topMargin=dp(7)})
  }
  packages.addView(filterRow)
  queue=LinearLayout(this).apply{orientation=1};queue.addView(label("Choose one or more PKG files to build your queue.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))});packages.addView(queue)
  body.addView(packages,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("03  /  SEND TO CONSOLE"))
  val transfer=card()
  status=label("READY  •  Select PKG files to begin.",12f,silver).apply{setPadding(2,dp(4),2,dp(14))};transfer.addView(status)
  send=button("START TRANSFER",true){sendFiles(false)}.apply{isEnabled=false};transfer.addView(send,lp(-1,dp(56)))
  pauseButton=button("PAUSE TRANSFER"){togglePause()}.apply{isEnabled=false};transfer.addView(pauseButton,lp(-1,dp(44)).apply{topMargin=dp(8)})
  retryButton=button("RETRY FAILED / PENDING"){sendFiles(true)}.apply{isEnabled=false};transfer.addView(retryButton,lp(-1,dp(44)).apply{topMargin=dp(8)})
  serverInfo=label("Local phone server  •  Port 9898",10f,muted).apply{setPadding(2,dp(12),2,2)};transfer.addView(serverInfo)
  stopServerButton=button("STOP FILE SERVER"){stopTransferServer()}.apply{isEnabled=false};transfer.addView(stopServerButton,lp(-1,dp(42)).apply{topMargin=dp(8)})
  body.addView(transfer,lp(-1,-2).apply{bottomMargin=dp(18)})
  body.addView(label("Both devices must be reachable on the same local network. You may switch apps while the XPD transfer notification is active; use STOP FILE SERVER when downloads are finished. A successful request means accepted by the receiver, not confirmed installation.",10f,muted).apply{setPadding(4,4,4,22);gravity=Gravity.CENTER})
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
      files.add(PkgEntry(java.util.UUID.randomUUID().toString(),u,displayPkgName(name),size,metadata))
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
 private fun chooseFolder(){
  if(busy||scanning)return
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }
  startActivityForResult(intent,42)
 }
 private fun scanFolder(treeUri:Uri){
  if(busy||scanning)return
  scanning=true;send.isEnabled=false
  status.text="Scanning folder for PlayStation .pkg files…"
  Thread{
   val discovered=mutableListOf<PkgEntry>()
   var inspected=0
   fun walk(folder:DocumentFile,depth:Int){
    if(depth>12||discovered.size>=2000)return
    val children=try{folder.listFiles()}catch(_:Exception){emptyArray()}
    for(child in children){
     if(discovered.size>=2000)break
     if(child.isDirectory){walk(child,depth+1)}
     else{
      val name=child.name?:continue
      if(!name.lowercase(Locale.ROOT).endsWith(".pkg"))continue
      inspected++
      if(PkgMetadataReader.hasPkgMagic(contentResolver,child.uri)){
       val meta=PkgMetadataReader.read(contentResolver,child.uri)
       discovered.add(PkgEntry("",child.uri,name,packageSize(child.uri),meta))
      }
     }
    }
   }
   try{
    val root=DocumentFile.fromTreeUri(this,treeUri)
    if(root!=null)walk(root,0)
   }catch(_:Exception){}
   runOnUiThread{
    var added=0
    discovered.forEach{entry->
     if(files.none{it.uri==entry.uri}){
      files.add(entry.copy(id=java.util.UUID.randomUUID().toString()));added++
     }
    }
    scanning=false;refreshQueue();saveState()
    status.text=if(added>0)"Folder scan complete: $added PKG file(s) added from $inspected candidate(s)." else "No new valid .pkg files found in this folder ($inspected candidates checked)."
   }
  }.start()
 }
 private fun chooseFiles(){if(busy||scanning)return;val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull()?.let { n -> n in 0..255 } == true}
 private fun discoverConsole(){
  status.text="Scanning your local Wi-Fi network…"
  Thread{
   val found=ConsoleDiscovery.scan(this){message->runOnUiThread{status.text=message}}
   runOnUiThread{
    when{
     found.isEmpty()->status.text="No compatible receiver found. Confirm that the PS4/PS5 receiver is running on the same Wi-Fi."
     found.size==1->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found[0].service} at ${found[0].address}:${found[0].port}. Check the connection before sending."}
     else->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found.size} possible receivers. Selected ${found[0].address} (${found[0].service}); verify before sending."}
    }
   }
  }.start()
 }
 private fun checkReceiver(){
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid port.";return}
  check.isEnabled=false;status.text="Checking PS4 receiver…"
  Thread{
   val result=when(p){
    9090->probeHttp(host,9090,"/status","GoldHEN")
    9021,9020->if(probeTcp(host,p))"GoldHEN binloader reachable on port $p. Ready for payload transfer." else "No GoldHEN binloader response on port $p."
    else->probeHttp(host,p,"/api","RPI")
   }
   runOnUiThread{status.text=result;check.isEnabled=true}
  }.start()
 }
 private fun probeTcp(host:String,p:Int):Boolean{return try{java.net.Socket().use{it.connect(java.net.InetSocketAddress(host,p),2500);true}}catch(_:Exception){false}}
 private fun probeHttp(host:String,p:Int,path:String,kind:String):String{
  var conn:HttpURLConnection?=null
  return try{
   conn=URL("http://$host:$p$path").openConnection() as HttpURLConnection
   conn.requestMethod="GET";conn.connectTimeout=4000;conn.readTimeout=4000
   val code=conn.responseCode
   val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(kind=="GoldHEN"&&body.contains("status")&&body.contains("ready"))
    "GoldHEN status endpoint is ready on 9090. The payload installer is available; a real console test is still required."
   else if(kind=="RPI"&&(body.contains("Unsupported method",true)&&body.contains("fail",true)))
    "RPI detected on port $p."
   else "Port $p responded (HTTP $code), but $kind was not confirmed."
  }catch(e:Exception){"No $kind response on $p: "+(e.message?:"connection failed")}finally{conn?.disconnect()}
 }
 private fun togglePause(){
  if(!busy)return
  paused=!paused
  pauseButton.text=if(paused)"RESUME TRANSFER" else "PAUSE TRANSFER"
  status.text=if(paused)"Paused after the current package request. Resume to continue." else "Transfer resumed."
 }
 private fun sendFiles(retryOnly:Boolean=false){
  if(busy)return
  if(scanning){status.text="Wait for the folder scan to finish.";return}
  val queueToSend=if(retryOnly)retryEntries.toList() else files.toList()
  if(queueToSend.isEmpty()){status.text=if(retryOnly)"There are no failed or pending packages to retry." else "Add at least one .pkg file.";return}
  val host=ip.text.toString().trim()
  val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  val goldHen=p==9090||p==9021||p==9020
  if(!goldHen&&p!=12800){status.text="Use RPI port 12800 or a GoldHEN binloader port (9090, 9021 or 9020).";return}
  if(files.any{it.metadata?.platform=="PS5"}){status.text="PS5 PKG metadata is supported, but PS5 receiver installation is not enabled in this build.";return}
  if(goldHen&&files.any{it.metadata?.platform!="PS4"}){status.text="GoldHEN installation currently accepts PS4 PKG files only.";return}
  val local=localIp(host)?:run{status.text="Could not find a phone IPv4 address on the same LAN as the console.";return}
  val activityRef=WeakReference(this)
  val queueSnapshot=queueToSend
  if(!retryOnly)retryEntries.clear() else retryEntries.clear()
  val servedCounts=served
  val lastUiRefresh=AtomicLong(0L)
  val s=PkgHttpServer(contentResolver,9898,queueSnapshot,publicHost=local,onBytes={id,n->
   servedCounts.compute(id){_,old->((old?:0L)+n).let{v->val size=queueSnapshot.firstOrNull{it.id==id}?.size?:-1L;if(size>0)v.coerceAtMost(size)else v}}
   val now=android.os.SystemClock.uptimeMillis()
   val previous=lastUiRefresh.get()
   if(now-previous>=200&&lastUiRefresh.compareAndSet(previous,now)){
    activityRef.get()?.runOnUiThread{activityRef.get()?.updatePackageProgress(id)}
   }
  },onServerError={m->activityRef.get()?.runOnUiThread{activityRef.get()?.status?.text=m}})
  TransferRuntime.server?.stop();TransferRuntime.server=null;server?.stop();server=null
  if(!s.start()){status.text="Could not start local HTTP server on port 9898.";return}
  server=s;TransferRuntime.server=s;stopServerButton.isEnabled=true
  try{
   val serviceIntent=Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_START)
   if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(serviceIntent)else startService(serviceIntent)
  }catch(e:Exception){
   s.stop();server=null;TransferRuntime.server=null;stopServerButton.isEnabled=false;status.text="Could not start transfer service: ${e.message?: "service error"}";return
  }
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Phone file server: http://$local:9898/  •  Keep XPD open"
  transferCancelled=false;paused=false;busy=true;pauseButton.text="PAUSE TRANSFER";pauseButton.isEnabled=true;retryButton.isEnabled=false;refreshQueue()
  status.text=if(goldHen)"Preparing GoldHEN installer…" else "Submitting packages to Remote Package Installer…"
  Thread{
   var message=""
   try{
    for((index,entry) in queueSnapshot.withIndex()){
     while(paused&&!transferCancelled)Thread.sleep(250)
     if(transferCancelled){message="Transfer stopped by user.";retryEntries.addAll(queueSnapshot.drop(index));break}
     runOnUiThread{status.text="Sending ${index+1}/${queueSnapshot.size}: ${entry.metadata?.displayTitle?:entry.name}"}
     if(goldHen){
      val result=GoldHenInstaller.install(this,host,local,entry,9898)
      if(!result.success){message="Package ${index+1} failed: ${result.message}";retryEntries.addAll(queueSnapshot.drop(index));break}
      message=result.message
     }else{
      var c:HttpURLConnection?=null
      val result=try{
       val fileUrl="http://$local:9898/pkg/${entry.id}"
       val body=JSONObject().put("type","direct").put("packages",JSONArray().put(fileUrl))
       val title=entry.metadata?.displayTitle?.takeIf{it.isNotBlank()}
       if(title!=null)body.put("name",title)
       if(entry.metadata?.iconPng!=null)body.put("icon_url","http://$local:9898/icon/${entry.id}")
       c=URL("http://$host:12800/api/install").openConnection() as HttpURLConnection
       c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true
       c.setRequestProperty("Content-Type","application/json")
       c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
       val code=c.responseCode
       val reply=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
       if(code in 200..299&&reply.contains("success",true))"RPI accepted: ${entry.name}" else "RPI rejected ${entry.name} (HTTP $code): $reply"
      }catch(e:Exception){"RPI request failed for ${entry.name}: ${e.message?: "network error"}"}
      finally{c?.disconnect()}
      if(!result.startsWith("RPI accepted:")){message=result;retryEntries.addAll(queueSnapshot.drop(index));break}
      message=result
     }
    }
    if(message.isBlank())message="No package request was completed."
    if(message.startsWith("RPI accepted:"))message="RPI accepted the last submitted package. Confirm downloads and installation on the console. $message"
   }catch(e:Exception){message="Transfer stopped: ${e.message?: "unexpected error"}"}
   runOnUiThread{
    status.text=message
    busy=false;paused=false;pauseButton.isEnabled=false;pauseButton.text="PAUSE TRANSFER";retryButton.isEnabled=retryEntries.isNotEmpty()
    refreshQueue()
   }
  }.start()
 }
 private fun stopTransferServer(){
  transferCancelled=true;paused=false
  try{startService(Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_STOP))}catch(_:Exception){
   TransferRuntime.server?.stop();TransferRuntime.server=null
  }
  server=null
  stopServerButton.isEnabled=false
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Local file server stopped."
  status.text="Transfer server stopped by user."
 }
 private fun localIp(targetIp:String?=null):String? {
  return try {
   val target=targetIp?.let{java.net.InetAddress.getByName(it).address}
   val all=NetworkInterface.getNetworkInterfaces()?:return null
   val interfaces=Collections.list(all).filter{it.isUp&&!it.isLoopback}
   if(target!=null){
    for(n in interfaces)for(ifa in n.interfaceAddresses){
     val addr=ifa.address as? Inet4Address?:continue
     val bytes=addr.address
     val prefix=ifa.networkPrefixLength.toInt().coerceIn(0,32)
     var same=true
     for(i in 0..3){
      val bits=(prefix-i*8).coerceIn(0,8)
      val mask=if(bits==0)0 else (0xff shl (8-bits)) and 0xff
      if((bytes[i].toInt() and 255 and mask)!=(target[i].toInt() and 255 and mask)){same=false;break}
     }
     if(same&&addr.isSiteLocalAddress)return addr.hostAddress
    }
   }
   for(n in interfaces)for(a in Collections.list(n.inetAddresses))if(a is Inet4Address&&a.isSiteLocalAddress&&!a.isLoopbackAddress)return a.hostAddress
   null
  }catch(_:Exception){null}
 }
 private fun refreshQueue(){
  val query=if(::queueSearch.isInitialized)queueSearch.text.toString().trim().lowercase(Locale.ROOT)else ""
  val familyCounts=files.mapNotNull{it.metadata?.titleId?.takeIf{v->v.isNotBlank()}}.groupingBy{it}.eachCount()
  val visible=files.withIndex().filter{(_,entry)->
   val meta=entry.metadata
   val roleOk=when(filterRole){"BASE"->meta!=null&&!meta.isDlc&&!meta.isUpdate;"UPDATE"->meta?.isUpdate==true;"DLC"->meta?.isDlc==true;else->true}
   val searchOk=query.isBlank()||listOf(entry.name,meta?.displayTitle.orEmpty(),meta?.titleId.orEmpty(),meta?.contentId.orEmpty()).any{it.lowercase(Locale.ROOT).contains(query)}
   roleOk&&searchOk
  }
  fileInfo.text=if(files.isEmpty())"No packages selected" else "${files.size} package(s) • showing ${visible.size}"
  queue.removeAllViews();progressViews.clear();progressLabels.clear()
  if(files.isEmpty()){queue.addView(label("Add PKG files or scan a folder to build your library.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))})}
  else if(visible.isEmpty()){queue.addView(label("No packages match this search or filter.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))})}
  visible.forEach{(index,f)->
   val row=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(10),dp(12),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(19,21,25));cornerRadius=dp(12).toFloat();setStroke(1,Color.rgb(45,48,55))}}
   val meta=f.metadata
   val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
   if(meta?.iconPng!=null){
    try{val bitmap=android.graphics.BitmapFactory.decodeByteArray(meta.iconPng,0,meta.iconPng.size);if(bitmap!=null){val cover=ImageView(this@MainActivity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP};top.addView(cover,lp(dp(58),dp(58)).apply{rightMargin=dp(12)})}}catch(_:Exception){}
   }
   val textCol=LinearLayout(this).apply{orientation=1}
   textCol.addView(label(meta?.displayTitle?.takeIf{it.isNotBlank()}?:f.name,13f,silver).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
   val role=when{meta?.isDlc==true->"DLC";meta?.isUpdate==true->"UPDATE";meta?.platform=="PS4"->"BASE GAME";else->""}
   val familyId=meta?.titleId.orEmpty()
   val familyCount=familyCounts[familyId]?:0
   val familyTag=if(familyCount>1)"FAMILY $familyCount" else ""
   val subtitle=listOfNotNull(meta?.platform,familyId.takeIf{it.isNotBlank()},meta?.version?.takeIf{it.isNotBlank()},role.takeIf{it.isNotBlank()},familyTag.takeIf{it.isNotBlank()}).joinToString("  •  ")
   if(subtitle.isNotBlank())textCol.addView(label(subtitle,10f,muted).apply{setPadding(0,dp(4),0,0)})
   top.addView(textCol,LinearLayout.LayoutParams(0,-2,1f));row.addView(top)
   val n=served[f.id]?:0L;val pct=if(f.size>0)(n*100/f.size).coerceIn(0,100)else 0
   val progressLabel=label("${f.name}  •  ${sizeText(f.size)}  •  served ${pct}%",10f,muted).apply{setPadding(0,dp(7),0,0);maxLines=2}
   row.addView(progressLabel);progressLabels[f.id]=progressLabel
   val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;this.progress=pct;progressTintList=android.content.res.ColorStateList.valueOf(silver);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(48,51,58))}
   row.addView(progress,lp(-1,dp(5)).apply{topMargin=dp(8)});progressViews[f.id]=progress
   val actions=LinearLayout(this).apply{orientation=0;gravity=Gravity.END}
   actions.addView(button("UP"){if(!busy&&index>0){Collections.swap(files,index,index-1);refreshQueue();saveState()}}.apply{isEnabled=!busy&&index>0},lp(dp(64),dp(32)).apply{rightMargin=dp(6)})
   actions.addView(button("DOWN"){if(!busy&&index<files.lastIndex){Collections.swap(files,index,index+1);refreshQueue();saveState()}}.apply{isEnabled=!busy&&index<files.lastIndex},lp(dp(72),dp(32)).apply{rightMargin=dp(6)})
   actions.addView(button("REMOVE"){if(!busy){files.removeAll{it.id==f.id};served.remove(f.id);refreshQueue();saveState()}},lp(dp(88),dp(32)))
   row.addView(actions,lp(-1,dp(34)).apply{topMargin=dp(8)})
   queue.addView(row,lp(-1,-2).apply{topMargin=dp(7)})
  }
  send.isEnabled=files.isNotEmpty()&&!busy;clear.isEnabled=!busy
  if(::retryButton.isInitialized&&!busy)retryButton.isEnabled=retryEntries.isNotEmpty()
 }
 private fun updatePackageProgress(id:String){
  val entry=files.firstOrNull{it.id==id}?:return
  val sent=served[id]?:0L
  val pct=if(entry.size>0)(sent*100/entry.size).coerceIn(0,100)else 0
  progressViews[id]?.progress=pct
  progressLabels[id]?.text="${entry.name}  •  ${sizeText(entry.size)}  •  served ${pct}%"
 }
 private fun sizeText(n:Long):String{if(n<0)return "Size unknown";val mb=n/1048576.0;return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024)else String.format(Locale.US,"%.1f MB",mb)}
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==42){
   val tree=data?.data
   if(resultCode==RESULT_OK&&tree!=null){
    val grantedFlags=((data?.flags?:0) and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try{contentResolver.takePersistableUriPermission(tree,grantedFlags)}catch(_:SecurityException){}
    scanFolder(tree)
   }
   return
  }
  if(requestCode!=41||resultCode!=RESULT_OK||data==null)return
  val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)};if(uris.isEmpty())data.data?.let{uris.add(it)}
  var count=0;var rejected=0;uris.forEach{u->if(files.any{it.uri==u})return@forEach
   try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){}
   if(!isValidPkg(u)){rejected++;return@forEach}
   val name=query(u,OpenableColumns.DISPLAY_NAME)?:"package.pkg"
   val metadata=PkgMetadataReader.read(contentResolver,u)
   files.add(PkgEntry(java.util.UUID.randomUUID().toString(),u,displayPkgName(name),packageSize(u),metadata));count++}
  status.text=when{count>0&&rejected>0->"$count valid PKG file(s) added; $rejected invalid file(s) skipped.";count>0->"$count valid PKG file(s) added.";rejected>0->"No valid PlayStation PKG found. Check the selected file.";else->"No new PKG files selected."};refreshQueue();saveState()
 }
 private fun displayPkgName(name:String)=if(name.lowercase(Locale.ROOT).endsWith(".pkg"))name else name+".pkg"
 private fun isValidPkg(u:Uri):Boolean = PkgMetadataReader.hasPkgMagic(contentResolver,u)
 private fun packageSize(u:Uri):Long { val column=query(u,OpenableColumns.SIZE)?.toLongOrNull(); if(column!=null&&column>=0)return column; return try{contentResolver.openFileDescriptor(u,"r")?.use{pfd->pfd.statSize.takeIf{it>=0L}?:android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use{it.channel.size()}}?:-1L}catch(_:Exception){-1L} }
 private fun query(u:Uri,col:String):String?{var c:Cursor?=null;return try{c=contentResolver.query(u,arrayOf(col),null,null,null);if(c!=null&&c.moveToFirst()){val i=c.getColumnIndex(col);if(i>=0&&!c.isNull(i))c.getString(i)else null}else null}catch(_:Exception){null}finally{c?.close()}}
 override fun onDestroy(){window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy()}
}
