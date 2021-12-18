package com.mob.multiappmodulecreate

import android.content.Context
import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mob.multiappmodulecreate.ui.theme.MultiAppModuleCreateTheme
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlSerializer
import java.io.*

@ExperimentalPermissionsApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiAppModuleCreateTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    PermissionRequest(
                        Modifier.fillMaxSize()
                    ) {
                        MainView(it)
                    }
                }
            }
        }
    }
}

@ExperimentalPermissionsApi
@Composable
fun PermissionRequest(modifier: Modifier, mainView: @Composable (modifier: Modifier) -> Unit) {
    val permissionState = rememberMultiplePermissionsState(
        mutableListOf(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.QUERY_ALL_PACKAGES
        )
    )
    PermissionsRequired(
        multiplePermissionsState = permissionState,
        permissionsNotGrantedContent = {
            LaunchedEffect(null) {
                permissionState.launchMultiplePermissionRequest()
            }
        },
        permissionsNotAvailableContent = {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(text = "没有权限")
            }
        }) {
        mainView(modifier)
    }
}

@Composable
fun MainView(modifier: Modifier) {
    var textFieldValue by remember { mutableStateOf("1") }
    var dialogText by remember { mutableStateOf("") }
    val openDialog = remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val context = LocalContext.current
    val composableScope = rememberCoroutineScope()
    Box(
        modifier = modifier.padding(
            start = 10.dp,
            end = 10.dp,
            bottom = 10.dp,
            top = 100.dp
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "一键制作多开Magisk补丁",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 30.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Realme UI 3.0根据配置文件获取允许多开的APP列表（ColorOS可能也是），" +
                        "本软件根据本机安装的软件进行生成相应的配置文件达到全软件可多开的目的。" +
                        "考虑到安装软件后，后续安装的软件有多开需求，" +
                        "可填入比现有模块版本号高的版本号即可通过升级模块扩展应用列表"
            )
            Spacer(modifier = Modifier.height(50.dp))
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = textFieldValue,
                onValueChange = { textFieldValue = it
                    .substring(0, if (it.length >= 4) 4 else it.length)
                    .filter { num -> num.isDigit() } },
                label = { Text(text = "版本号") },
                trailingIcon = @Composable {
                    if (textFieldValue.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            modifier = Modifier.clickable { textFieldValue = "" }
                        )
                    }
                },
                placeholder = @Composable { Text(text = "请输入版本号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(5.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (textFieldValue.isBlank()) {
                        dialogText = "请输入版本号"
                        openDialog.value = true
                    } else {
                        status = "进行中..."
                        composableScope.launch {
                            status = createModule(context, textFieldValue.toInt())
                        }
                    }
                }
            ) {
                Text(text = "开始制作Magisk补丁")
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(text = status)
            ShowDialog(dialogText, openDialog)
        }
    }
}

@Composable
private fun ShowDialog(text: String, openDialog: MutableState<Boolean>) {
    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = {
                openDialog.value = false
            },
            title = { Text(text = "提示") },
            text = { Text(text = text) },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false }) {
                    Text(text = "好的")
                }
            }
        )
    }
}

private fun getPackageName(): List<String>{
    val exec = Runtime.getRuntime().exec("pm list packages -3")
    val isr = InputStreamReader(exec.inputStream, "utf-8")
    return BufferedReader(isr).lineSequence().map {
        it.removePrefix("package:")
    }.toList()
}


private fun createModule(context: Context, version: Int): String {
    return try {
        val buildPath = "${context.externalCacheDir}/build"
        deleteDirWithFile(File(buildPath))
        buildSysMultiAppConfig(buildPath, getPackageName())
        createModuleProp(buildPath, version)
        createCustomize(buildPath)
        Utils.copyAssetsFile2AnyWhere(context, "update-binary", "$buildPath/META-INF/com/google/android")
        Utils.copyAssetsFile2AnyWhere(context, "updater-script", "$buildPath/META-INF/com/google/android")
        Utils.copyAssetsFile2AnyWhere(context, "OplusMultiApp.apk", "$buildPath/system/system_ext/app")
        Utils.copyAssetsFile2AnyWhere(context, "OplusMultiApp.odex", "$buildPath/system/system_ext/app/OplusMultiApp/oat/arm64")
        Utils.copyAssetsFile2AnyWhere(context, "OplusMultiApp.vdex", "$buildPath/system/system_ext/app/OplusMultiApp/oat/arm64")
        Utils.zipDir(buildPath, "${context.externalCacheDir}/多开模块V$version.zip")
        Utils.copyFileToDownloads(context, File("${context.externalCacheDir}/多开模块V$version.zip"))
        deleteDirWithFile(File(context.externalCacheDir!!.path))
        "成功,文件路径:${Utils.DOWNLOAD_DIR.path}/多开模块V$version.zip"
    } catch (e: Exception) {
        try {
            deleteDirWithFile(File(context.externalCacheDir!!.path))
        } catch (ignored: Exception) { }
        "失败：${e.message.toString()}"
    }

}

private fun createModuleProp(buildPath: String, version: Int): Boolean {
    return try {
        val file = File("$buildPath/module.prop")
        file.createNewFile()
        val os = FileOutputStream(file)
        val nextLine = System.getProperty("line.separator")
        os.apply {
            write("id=com.mob.MultiAppModuleCreate".toByteArray())
            write(nextLine?.toByteArray())
            write("name=Realme UI 3.0应用分身修改".toByteArray())
            write(nextLine?.toByteArray())
            write("version=V$version.0".toByteArray())
            write(nextLine?.toByteArray())
            write("versionCode=$version".toByteArray())
            write(nextLine?.toByteArray())
            write("author=酷安@影山茂夫".toByteArray())
            write(nextLine?.toByteArray())
            write("description=此模块为动态生成".toByteArray())
            write(nextLine?.toByteArray())
        }
        os.close()
        true
    } catch (ignored: Exception) {
        throw IOException("创建module.prop失败")
    }
}

private fun createCustomize(buildPath: String): Boolean {
    return try {
        val file = File("$buildPath/customize.sh")
        file.createNewFile()
        val os = FileOutputStream(file)
        os.apply {
            write("REPLACE=\"/system/system_ext/oplus/sys_multi_app_config.xml\"".toByteArray())
        }
        os.close()
        true
    } catch (ignored: Exception) {
        throw IOException("创建customize.zh失败")
    }
}

private fun buildSysMultiAppConfig(buildPath: String, packageName: List<String>): Boolean {
    return try {
        val dirPath = "$buildPath/system/system_ext/oplus"
        val fileName = "sys_multi_app_config.xml"
        val dirFile = File(dirPath)
        val file = File("$dirPath/$fileName")
        dirFile.mkdirs()
        file.createNewFile()
        val os = FileOutputStream(file)
        val serializer = Xml.newSerializer().apply {
            setOutput(os, "UTF-8")
            startDocument("UTF-8", true)
            text(System.getProperty("line.separator"))
        }.also {
            it.createConfigVersion {
                it.createRelate()
                it.createAllowed(packageName)
                it.createOPAllowed()
                it.createChooseRecentTask()
                it.createChooseSkipChoose()
                it.createCrossAuthority()
            }
        }
        serializer.apply {
            endDocument()
            flush()
        }
        os.close()
        true
    } catch (ignored: Throwable) {
        throw IOException("创建sys_multi_app_config.xml失败")
    }
}

private fun XmlSerializer.createConfigVersion(content: () -> Unit) {
    startTag(null, "configVersion")
    attribute(null, "name", "2021.09.13")
    attribute(null, "number", "20210913")
    text(System.getProperty("line.separator"))
    startTag(null, "androidVersion")
    attribute(null, "name", "AndroidS")
    attribute(null, "number", "31")
    endTag(null, "androidVersion")
    text(System.getProperty("line.separator"))
    startTag(null, "maxNum")
    attribute(null, "name", "1000")
    endTag(null, "maxNum")
    text(System.getProperty("line.separator"))
    content.invoke()
    text(System.getProperty("line.separator"))
    endTag(null, "configVersion")
}

private fun XmlSerializer.createRelate() {
    val packageName = mutableListOf(
        "android",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.ext.services",
        "com.google.android.webview",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.webview",
        "com.oplus.securitypermission",
        "com.facebook.services",
        "com.facebook.system",
        "com.facebook.appmanager",
        "com.android.providers.media"
    )
    createTagAndItem("related", packageName)
}

private fun XmlSerializer.createAllowed(packageName: List<String>) {
    createTagAndItem("allowed", packageName)
}

private fun XmlSerializer.createOPAllowed() {
    val packageName = mutableListOf(
        "com.alibaba.mobileim",
        "com.qzone",
        "com.ifreetalk.ftalk",
        "com.baidu.tieba",
        "com.p1.mobile.putong",
        "com.tencent.qqlite",
        "com.tencent.tim",
        "com.soft.blued",
        "com.duowan.mobile",
        "com.immomo.momo",
        "com.weico.international",
        "com.linkedin.android",
        "com.facebook.lite",
        "com.tinder",
        "com.snapchat.android",
        "com.pinterest",
        "com.tumblr",
        "com.myyearbook.m",
        "com.quora.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.badoo.mobile",
        "com.sgiggle.production",
        "com.kakao.talk",
        "com.hellostudio.hellotalk",
        "net.tandem",
        "kik.android",
        "foxycorp.textnow",
        "com.enflick.android.TextNow",
        "com.ubercab",
        "com.ubercab.driver",
        "net.one97.paytm",
        "com.olacabs.customer",
        "com.imo.android.imoim",
        "com.discord",
        "org.thunderdog.challegram",
        "com.zoosk.zoosk",
        "com.okcupid.okcupid",
        "com.match.android.matchmobile"
    )
    createTagAndItem("opallowed", packageName)
}

private fun XmlSerializer.createChooseRecentTask() {
    val packageName = mutableListOf(
        "com.sina.weibo/.wxapi.WXEntryActivity",
        "com.tencent.mobileqq/.wxapi.WXEntryActivity",
        "com.xunmeng.pinduoduo/.wxapi.WXEntryActivity",
        "com.tencent.tmgp.sgame/.wxapi.WXEntryActivity",
        "com.tencent.mm/.plugin.webview.ui.tools.QQCallbackUI",
        "com.taobao.taobao/com.taobao.login4android.activity.AlipaySSOResultActivity",
        "com.taobao.taobao/.apshare.ShareEntryActivity",
        "com.taobao.taobao/.weibo.WeiboShareActivity",
        "com.alibaba.android.rimet/com.alibaba.android.rimet.apshare.ShareEntryActivity",
        "com.xunmeng.pinduoduo/com.xunmeng.pinduoduo.auth.pay.qqpay.QQCallbackActivity",
        "com.xunmeng.pinduoduo/com.tencent.tauth.AuthActivity",
        "com.taobao.idlefish/com.taobao.idlefish.ResultActivity",
        "com.alibaba.android.rimet/com.alipay.sdk.app.AlipayResultActivity",
        "*/com.sina.weibo.sdk.share.WbShareResultActivity",
        "*/com.tencent.tauth.AuthActivity",
        "*/.wxapi.WXEntryActivity",
        "*/.wxapi.WXPayEntryActivity",
        "*/com.tencent.midas.wx.APMidasWXPayActivity",
        "*/com.tencent.midas.qq.APMidasQQWalletActivity",
        "*/.sinaapi.WBShareActivity",
        "*/com.alipay.sdk.app.AlipayResultActivity",
        "*/com.alibaba.android.rimet.wxapi.WXEntryActivity"
    )
    createTagAndItem("chooseRecentTask", packageName)
}

private fun XmlSerializer.createChooseSkipChoose() {
    val packageName = mutableListOf(
        "com.sina.weibo/com.taobao.share.view.WeiboShareActivity",
        "com.sina.weibo/.composerinde.ComposerDispatchActivity"
    )
    startTag(null, "chooseSkipChoose")
    text(System.getProperty("line.separator"))
    startTag(null, "pkg")
    attribute(null, "name", "*")
    text(System.getProperty("line.separator"))
    packageName.forEach {
        startTag(null, "item")
        attribute(null, "name", it)
        endTag(null, "item")
        text(System.getProperty("line.separator"))
    }
    endTag(null, "pkg")
    endTag(null, "chooseSkipChoose")
    text(System.getProperty("line.separator"))
}

private fun XmlSerializer.createCrossAuthority() {
    val packageName = mutableListOf(
        "media",
        "mms",
        "downloads",
        "settings",
        "com.android.calendar",
        "com.android.contacts",
        "com.android.badge",
        "contacts;com.android.contacts",
        "com.android.contacts.files",
        "com.android.externalstorage.documents",
        "com.android.providers.media.documents",
        "com.google.android.apps.photos.contentprovider",
        "com.google.android.apps.docs.storage",
        "com.sina.weibo.sdkProvider",
    )
    createTagAndItem("crossAuthority", packageName)
}

private fun XmlSerializer.createTagAndItem(tagName: String, nameList: List<String>) {
    startTag(null, tagName)
    text(System.getProperty("line.separator"))
    nameList.forEach {
        startTag(null, "item")
        attribute(null, "name", it)
        endTag(null, "item")
        text(System.getProperty("line.separator"))
    }
    endTag(null, tagName)
    text(System.getProperty("line.separator"))
}

private fun deleteDirWithFile(dir: File) {
    if (!dir.exists() && !dir.isDirectory) return
    dir.listFiles()?.forEach {
        if (it.isFile) it.delete()
        else if (it.isDirectory) deleteDirWithFile(it)
    }
    dir.delete()
}