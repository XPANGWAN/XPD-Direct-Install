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
import android.util.Base64
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
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:LinearLayout; private lateinit var queueSearch:EditText; private lateinit var ps5Advanced:LinearLayout
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var clear:Button; private lateinit var stopServerButton:Button; private lateinit var pauseButton:Button; private lateinit var retryButton:Button; private lateinit var ps5WebPort:EditText; private lateinit var installPs5ReceiverButton:Button; private lateinit var serverInfo:TextView
 @Volatile private var busy=false; @Volatile private var scanning=false; @Volatile private var transferCancelled=false; @Volatile private var paused=false; @Volatile private var ps5ReceiverBusy=false; private val retryEntries=mutableListOf<PkgEntry>(); private val expandedFamilies=mutableSetOf<String>(); private var filterRole="ALL"
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi();server=TransferRuntime.server;stopServerButton.isEnabled=server!=null;if(server!=null)serverInfo.text="Local file server active on port 9898.";restoreState();if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),9098)}
 private fun buildUi(){
  val root=LinearLayout(this).apply{
   orientation=1
   setPadding(dp(16),dp(8),dp(16),dp(12))
   setBackgroundColor(bg)
  }

  // Upstream-inspired Android hierarchy: compact toolbar, console card,
  // library header/list, primary send action and a dedicated status card.
  val toolbar=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val mark=ImageView(this).apply{
   setImageResource(R.drawable.ic_xpd_mark)
   scaleType=ImageView.ScaleType.FIT_CENTER
   contentDescription="XPD"
  }
  toolbar.addView(mark,lp(dp(34),dp(34)).apply{rightMargin=dp(10)})
  val brand=LinearLayout(this).apply{orientation=1}
  brand.addView(label("XPD DIRECT INSTALL",18f,Color.WHITE).apply{
   typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD)
   letterSpacing=.015f
  })
  brand.addView(label("PLAYSTATION PACKAGE SENDER",9f,muted).apply{letterSpacing=.09f})
  toolbar.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
  root.addView(toolbar,lp(-1,dp(44)).apply{bottomMargin=dp(10)})

  val connection=card()
  val connectionHead=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val connectionTitles=LinearLayout(this).apply{orientation=1}
  connectionTitles.addView(label("PLAYSTATION",11f,silver).apply{
   typeface=android.graphics.Typeface.DEFAULT_BOLD;letterSpacing=.12f
  })
  connectionTitles.addView(label("Connect your console",13f,Color.WHITE).apply{setPadding(0,dp(3),0,0)})
  connectionHead.addView(connectionTitles,LinearLayout.LayoutParams(0,-2,1f))
  val connectionToggle=button("HIDE  −"){
   val show=connectionBody.visibility!=View.VISIBLE
   connectionBody.visibility=if(show)View.VISIBLE else View.GONE
  }
  connectionHead.addView(connectionToggle,lp(dp(86),dp(34)))
  connection.addView(connectionHead)

  val connectionBody=LinearLayout(this).apply{orientation=1}
  ip=field("Console IP address",false).apply{
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
  }
  port=field("12800",true).apply{setText("12800")}
  val addressRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val ipCol=LinearLayout(this).apply{orientation=1}
  ipCol.addView(label("IP ADDRESS",9f,muted).apply{setPadding(0,0,0,dp(4))})
  ipCol.addView(ip,lp(-1,dp(46)))
  addressRow.addView(ipCol,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(8)})
  val portCol=LinearLayout(this).apply{orientation=1}
  portCol.addView(label("PORT",9f,muted).apply{setPadding(0,0,0,dp(4))})
  portCol.addView(port,lp(-1,dp(46)))
  addressRow.addView(portCol,lp(dp(78),-2))
  check=button("TEST",true){checkReceiver()}
  addressRow.addView(check,lp(dp(68),dp(46)).apply{leftMargin=dp(8);topMargin=dp(13)})
  connectionBody.addView(addressRow,lp(-1,-2).apply{topMargin=dp(12)})
  connectionBody.addView(button("FIND CONSOLE"){discoverConsole()}.apply{
   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search,0,0,0)
   compoundDrawablePadding=dp(7)
  },lp(-1,dp(40)).apply{topMargin=dp(8)})
  ps5Advanced=LinearLayout(this).apply{
   orientation=1;visibility=View.GONE;setPadding(0,dp(10),0,0)
  }
  ps5Advanced.addView(label("PS5 RECEIVER SETUP",9f,silver).apply{
   typeface=android.graphics.Typeface.DEFAULT_BOLD;letterSpacing=.10f
  })
  ps5Advanced.addView(label("Open Payload Manager on your PS5 before installing the receiver.",10f,muted).apply{
   setPadding(0,dp(4),0,dp(6));maxLines=2
  })
  ps5WebPort=field("8084",true).apply{setText("8084")}
  ps5Advanced.addView(label("PAYLOAD MANAGER PORT",9f,muted).apply{setPadding(0,0,0,dp(4))})
  ps5Advanced.addView(ps5WebPort,lp(-1,dp(42)))
  installPs5ReceiverButton=button("INSTALL PS5 RECEIVER",true){installPs5Receiver()}
  ps5Advanced.addView(installPs5ReceiverButton,lp(-1,dp(44)).apply{topMargin=dp(8)})
  connectionBody.addView(ps5Advanced)
  connectionBody.addView(button("PS5 RECEIVER OPTIONS  +"){
   ps5Advanced.visibility=if(ps5Advanced.visibility==View.VISIBLE)View.GONE else View.VISIBLE
  },lp(-1,dp(36)).apply{topMargin=dp(7)})
  connection.addView(connectionBody)
  root.addView(connection,lp(-1,-2).apply{bottomMargin=dp(14)})

  val libraryHeader=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val libraryTitles=LinearLayout(this).apply{orientation=1}
  libraryTitles.addView(label("LIBRARY",19f,Color.WHITE).apply{
   typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD)
  })
  fileInfo=label("0 packages",10f,muted).apply{setPadding(0,dp(2),0,0)}
  libraryTitles.addView(fileInfo)
  libraryHeader.addView(libraryTitles,LinearLayout.LayoutParams(0,-2,1f))
  clear=button("CLEAR"){if(!busy){files.clear();retryEntries.clear();served.clear();refreshQueue();saveState()}}.apply{
   isEnabled=!busy
   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_trash,0,0,0)
   compoundDrawablePadding=dp(4)
  }
  libraryHeader.addView(clear,lp(dp(78),dp(36)))
  root.addView(libraryHeader,lp(-1,-2).apply{bottomMargin=dp(8)})

  val addRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val add=button("ADD PKG FILES",true){chooseFiles()}.apply{
   textSize=13f
   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0)
   compoundDrawablePadding=dp(7)
  }
  addRow.addView(add,LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(7)})
  val scan=button("SCAN FOLDER"){chooseFolder()}.apply{
   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_open,0,0,0)
   compoundDrawablePadding=dp(5)
  }
  addRow.addView(scan,LinearLayout.LayoutParams(0,dp(48),1f))
  root.addView(addRow)

  queueSearch=field("Search games or Title ID",false).apply{
   addTextChangedListener(object:android.text.TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshQueue()}
    override fun afterTextChanged(s:android.text.Editable?){}
   })
  }
  root.addView(queueSearch,lp(-1,dp(42)).apply{topMargin=dp(8)})
  val filterRow=LinearLayout(this).apply{orientation=0}
  listOf("ALL","BASE","UPDATE","DLC").forEach{tag->
   filterRow.addView(button(tag,tag=="ALL"){filterRole=tag;refreshQueue()},
    LinearLayout.LayoutParams(0,dp(34),1f).apply{rightMargin=dp(4)})
  }
  root.addView(filterRow,lp(-1,-2).apply{topMargin=dp(6)})

  val listScroll=ScrollView(this).apply{
   clipToPadding=false;isFillViewport=false;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
  }
  queue=LinearLayout(this).apply{orientation=1}
  queue.addView(label("No packages yet. Add PKG files to build your send queue.",11f,muted).apply{
   setPadding(dp(2),dp(14),dp(2),dp(10));maxLines=2
  })
  listScroll.addView(queue)
  root.addView(listScroll,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=dp(7)})

  send=button("SEND QUEUE",true){sendFiles(false)}.apply{
   isEnabled=false;textSize=15f
   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send,0,0,0)
   compoundDrawablePadding=dp(8)
  }
  root.addView(send,lp(-1,dp(54)).apply{topMargin=dp(10)})

  val statusCard=card().apply{setPadding(dp(13),dp(10),dp(13),dp(10))}
  status=label("Add PKG files, connect, then send.",11f,muted).apply{
   maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
  }
  statusCard.addView(status)
  val actionRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  pauseButton=button("PAUSE"){togglePause()}.apply{isEnabled=false}
  actionRow.addView(pauseButton,LinearLayout.LayoutParams(0,dp(34),1f).apply{rightMargin=dp(6)})
  retryButton=button("RETRY FAILED"){sendFiles(true)}.apply{isEnabled=false}
  actionRow.addView(retryButton,LinearLayout.LayoutParams(0,dp(34),1f))
  statusCard.addView(actionRow,lp(-1,dp(34)).apply{topMargin=dp(7)})
  val serverRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  serverInfo=label("Local file server stopped",9f,muted).apply{
   maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
  }
  serverRow.addView(serverInfo,LinearLayout.LayoutParams(0,-2,1f))
  stopServerButton=button("STOP SERVER"){stopTransferServer()}.apply{
   isEnabled=false;textSize=10f
  }
  serverRow.addView(stopServerButton,lp(dp(104),dp(30)).apply{leftMargin=dp(6)})
  statusCard.addView(serverRow,lp(-1,-2).apply{topMargin=dp(5)})
  root.addView(statusCard,lp(-1,-2).apply{topMargin=dp(8)})

  setContentView(root)
 }
 private fun prefs()=getSharedPreferences("xpd_direct_install",MODE_PRIVATE)
 private fun restoreState(){
  val savedIp=prefs().getString("console_ip","")?:""
  if(savedIp.isNotEmpty())ip.setText(savedIp)
  val savedPort=prefs().getString("console_port","12800")?:"12800"
  port.setText(savedPort)
  ps5WebPort.setText(prefs().getString("ps5_web_port","8084")?:"8084")
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
   .putString("ps5_web_port",if(::ps5WebPort.isInitialized)ps5WebPort.text.toString().trim() else "8084")
   .putString("pkg_uris",arr.toString()).apply()
 }
 override fun onResume(){super.onResume();if(::stopServerButton.isInitialized){server=TransferRuntime.server;stopServerButton.isEnabled=server!=null;if(server==null)serverInfo.text="Local file server stopped.";refreshQueue()}}
 override fun onPause(){saveState();super.onPause()}
 private fun sectionHeader(s:String)=label(s,10f,silver).apply{letterSpacing=.12f;setPadding(2,0,2,8)}
 private fun card()=LinearLayout(this).apply{
  orientation=1;setPadding(14,14,14,14)
  background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=18f;setStroke(1,Color.rgb(48,51,58))}
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c);includeFontPadding=false;setLineSpacing(dp(2).toFloat(),1f)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;isAllCaps=false;textSize=12f;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;includeFontPadding=false;minHeight=0;minimumHeight=0;setPadding(dp(10),dp(9),dp(10),dp(9));setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
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
    if(depth>12||discovered.size>=2000||inspected>=10000)return
    val children=try{folder.listFiles()}catch(_:Exception){emptyArray()}
    for(child in children){
     if(discovered.size>=2000||inspected>=10000)break
     if(child.isDirectory){walk(child,depth+1)}
     else{
      val name=child.name?:continue
      inspected++
      if(!name.lowercase(Locale.ROOT).endsWith(".pkg"))continue
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
    status.text=if(added>0)"Folder scan complete: $added PKG file(s) added from $inspected file(s) inspected." else "No new valid .pkg files found in this folder ($inspected files inspected)."
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
    "PKG receiver detected on port $p (PS4 RPI / PS5 receiver). "
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
  if(ps5ReceiverBusy){status.text="Wait for PS5 receiver deployment to finish.";return}
  if(TransferRuntime.server!=null){status.text="The previous file server is still active. Stop it after the console finishes downloading before starting another queue.";return}
  if(scanning){status.text="Wait for the folder scan to finish.";return}
  val queueToSend=if(retryOnly)retryEntries.toList() else files.toList()
  if(queueToSend.isEmpty()){status.text=if(retryOnly)"There are no failed or pending packages to retry." else "Add at least one .pkg file.";return}
  val host=ip.text.toString().trim()
  val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  val goldHen=p==9090||p==9021||p==9020
  if(!goldHen&&p!=12800){status.text="Use RPI port 12800 or a GoldHEN binloader port (9090, 9021 or 9020).";return}
  if(goldHen&&queueToSend.any{it.metadata?.platform!="PS4"}){status.text="GoldHEN installation currently accepts PS4 PKG files only.";return}
  val local=localIp(host)?:run{status.text="Could not find a phone IPv4 address on the same LAN as the console.";return}
  val activityRef=WeakReference(this)
  val queueSnapshot=queueToSend
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
  retryEntries.clear()
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Background file server active: http://$local:9898/"
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
       val accepted=try{val response=JSONObject(reply);response.optBoolean("success",false)||response.optString("status").equals("success",true)}catch(_:Exception){false}
       if(code in 200..299&&accepted)"RPI accepted: ${entry.name}" else "RPI rejected ${entry.name} (HTTP $code): $reply"
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
 private fun installPs5Receiver(){
  if(busy||scanning){status.text="Wait until the current operation finishes.";return}
  val host=ip.text.toString().trim()
  val webPort=ps5WebPort.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter the PS5 IPv4 address first.";return}
  if(webPort==null||webPort !in 1..65535){status.text="Enter a valid Payload Manager web port (default 8084).";return}
  installPs5ReceiverButton.isEnabled=false;ps5ReceiverBusy=true
  status.text="Preparing the PS5 receiver payload…"
  Thread{
   var message=""
   try{
    val encoded=assets.open("pkg-receiver.elf.b64").bufferedReader().use{it.readText()}
    val elf=Base64.decode(encoded,Base64.DEFAULT)
    if(elf.isEmpty())throw IllegalStateException("PS5 receiver payload is empty")
    val upload=URL("http://$host:$webPort/manage:upload?filename=pkg-receiver.elf").openConnection() as HttpURLConnection
    try{
     upload.requestMethod="POST";upload.connectTimeout=15000;upload.readTimeout=90000;upload.doOutput=true
     upload.setRequestProperty("Content-Type","application/octet-stream")
     upload.setFixedLengthStreamingMode(elf.size)
     upload.outputStream.use{it.write(elf);it.flush()}
     val code=upload.responseCode
     val body=(if(code in 200..299)upload.inputStream else upload.errorStream)?.bufferedReader()?.use{it.readText()}?:""
     if(code !in 200..299||!body.trim().startsWith("OK",true))throw IllegalStateException("Receiver upload failed (HTTP $code): $body")
    }finally{upload.disconnect()}
    val launch=URL("http://$host:$webPort/loadpayload:pkg-receiver.elf").openConnection() as HttpURLConnection
    try{
     launch.requestMethod="GET";launch.connectTimeout=10000;launch.readTimeout=30000
     val code=launch.responseCode
     val body=(if(code in 200..299)launch.inputStream else launch.errorStream)?.bufferedReader()?.use{it.readText()}?:""
     if(code !in 200..299||!body.contains("OK",true))throw IllegalStateException("Receiver uploaded, but launch failed (HTTP $code): $body")
     message="PS5 receiver launched successfully. It listens on port 12800; you can now send PS5 PKG files."
    }finally{launch.disconnect()}
   }catch(e:Exception){message="Could not install PS5 receiver: ${e.message?: "network error"}"}
   runOnUiThread{status.text=message;ps5ReceiverBusy=false;installPs5ReceiverButton.isEnabled=true}
  }.start()
 }
 private fun stopTransferServer(){
  transferCancelled=true;paused=false
  TransferRuntime.server?.stop();TransferRuntime.server=null
  try{startService(Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_STOP))}catch(_:Exception){}
  server=null
  stopServerButton.isEnabled=false
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Local file server stopped."
  status.text="Transfer server stopped by user."
  refreshQueue()
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
  fileInfo.text=when{
   files.isEmpty()->"No files selected"
   else->"${files.size} selected  |  ${visible.size} shown"
  }
  queue.removeAllViews();progressViews.clear();progressLabels.clear()
  if(files.isEmpty()){
   queue.addView(label("No packages yet. Tap ADD PKG FILES to begin.",11f,muted).apply{setPadding(2,dp(12),2,dp(8))})
  }else if(visible.isEmpty()){
   queue.addView(label("No packages match this search or filter.",11f,muted).apply{setPadding(2,dp(12),2,dp(8))})
  }
  val groupedVisible=visible.groupBy{(_,entry)->entry.metadata?.titleId?.takeIf{it.isNotBlank()}?:("single:"+entry.id)}
  groupedVisible.forEach{(familyKey,familyItems)->
   val familyTitleId=familyItems.firstOrNull()?.value?.metadata?.titleId.orEmpty()
   val familyTotal=if(familyTitleId.isNotBlank())familyCounts[familyTitleId]?:familyItems.size else familyItems.size
   if(familyTotal>1){
    val expanded=familyKey in expandedFamilies
    val familyButton=button("GAME FAMILY  |  $familyTitleId  |  $familyTotal FILES  |  ${if(expanded)"HIDE" else "SHOW"}"){
     if(expanded)expandedFamilies.remove(familyKey)else expandedFamilies.add(familyKey)
     refreshQueue()
    }.apply{isEnabled=!busy;textSize=10f}
    queue.addView(familyButton,lp(-1,dp(38)).apply{topMargin=dp(8)})
   }
   if(familyTotal<=1||familyKey in expandedFamilies){
    familyItems.forEach{(index,f)->
     val row=LinearLayout(this).apply{
      orientation=1
      setPadding(dp(10),dp(10),dp(10),dp(9))
      background=android.graphics.drawable.GradientDrawable().apply{
       setColor(Color.rgb(19,21,25));cornerRadius=dp(12).toFloat()
       setStroke(1,Color.rgb(45,48,55))
      }
     }
     val meta=f.metadata
     val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
     val pkgMark=label("PKG",9f,silver).apply{
      gravity=Gravity.CENTER
      typeface=android.graphics.Typeface.DEFAULT_BOLD
      background=android.graphics.drawable.GradientDrawable().apply{
       setColor(Color.rgb(38,41,47));cornerRadius=dp(8).toFloat()
       setStroke(1,Color.rgb(75,79,88))
      }
     }
     top.addView(pkgMark,lp(dp(44),dp(44)).apply{rightMargin=dp(10)})
     if(meta?.iconPng!=null){
      try{
       val bitmap=android.graphics.BitmapFactory.decodeByteArray(meta.iconPng,0,meta.iconPng.size)
       if(bitmap!=null){
        val cover=ImageView(this@MainActivity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP}
        top.addView(cover,lp(dp(44),dp(44)).apply{rightMargin=dp(10)})
       }
      }catch(_:Exception){}
     }
     val textCol=LinearLayout(this).apply{orientation=1}
     textCol.addView(label(meta?.displayTitle?.takeIf{it.isNotBlank()}?:f.name,13f,silver).apply{
      maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
      typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
     })
     val role=when{meta?.isDlc==true->"DLC";meta?.isUpdate==true->"UPDATE";meta!=null->"BASE GAME";else->"PKG"}
     val details=listOfNotNull(meta?.platform,meta?.titleId?.takeIf{it.isNotBlank()},meta?.version?.takeIf{it.isNotBlank()},role).joinToString("  |  ")
     textCol.addView(label(details,9f,muted).apply{
      setPadding(0,dp(4),0,0);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
     })
     top.addView(textCol,LinearLayout.LayoutParams(0,-2,1f))
     row.addView(top)

     val n=served[f.id]?:0L
     val pct=if(f.size>0)(n*100/f.size).coerceIn(0L,100L).toInt()else 0
     val progressLabel=label(
      if(n>0)"${sizeText(f.size)}  |  ${pct}% served" else sizeText(f.size),
      9f,muted
     ).apply{setPadding(2,dp(7),2,0);maxLines=1}
     row.addView(progressLabel);progressLabels[f.id]=progressLabel
     val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
      max=100;this.progress=pct
      progressTintList=android.content.res.ColorStateList.valueOf(silver)
      progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(48,51,58))
      visibility=if(n>0)View.VISIBLE else View.GONE
     }
     row.addView(progress,lp(-1,dp(4)).apply{topMargin=dp(6)})
     progressViews[f.id]=progress

     val options=button("FILE OPTIONS"){ 
      val popup=PopupMenu(this@MainActivity,row.getChildAt(row.childCount-1))
      popup.menu.add(0,1,0,"Move up").isEnabled=!busy&&index>0
      popup.menu.add(0,2,1,"Move down").isEnabled=!busy&&index<files.lastIndex
      popup.menu.add(0,3,2,"Remove file").isEnabled=!busy
      popup.setOnMenuItemClickListener{item->
       when(item.itemId){
        1->{if(!busy&&index>0){Collections.swap(files,index,index-1);refreshQueue();saveState()}}
        2->{if(!busy&&index<files.lastIndex){Collections.swap(files,index,index+1);refreshQueue();saveState()}}
        3->{if(!busy){files.removeAll{it.id==f.id};retryEntries.removeAll{it.id==f.id};served.remove(f.id);refreshQueue();saveState()}}
       }
       true
      }
      popup.show()
     }.apply{textSize=10f}
     row.addView(options,lp(-1,dp(32)).apply{topMargin=dp(7)})
     queue.addView(row,lp(-1,-2).apply{topMargin=dp(7)})
    }
   }
  }
  send.isEnabled=files.isNotEmpty()&&!busy&&TransferRuntime.server==null
  clear.isEnabled=!busy
  if(::retryButton.isInitialized&&!busy)retryButton.isEnabled=retryEntries.isNotEmpty()&&TransferRuntime.server==null
 }
 private fun updatePackageProgress(id:String){
  val entry=files.firstOrNull{it.id==id}?:return
  val sent=served[id]?:0L
  val pct=if(entry.size>0)(sent*100/entry.size).coerceIn(0L,100L).toInt()else 0
  progressViews[id]?.progress=pct
  progressViews[id]?.visibility=if(sent>0L)View.VISIBLE else View.GONE
  progressLabels[id]?.text=if(sent>0L)"${sizeText(entry.size)}  |  ${pct}% served" else sizeText(entry.size)
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
