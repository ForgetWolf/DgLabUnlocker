package sakura.kooi.dglabunlocker;

import static sakura.kooi.dglabunlocker.utils.MapUtils.entry;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sakura.kooi.dglabunlocker.injector.IHookPointInjector;
import sakura.kooi.dglabunlocker.injector.InjectBluetoothServiceReceiver;
import sakura.kooi.dglabunlocker.injector.InjectBugReportDialog;
import sakura.kooi.dglabunlocker.injector.InjectControlledStrengthButton;
import sakura.kooi.dglabunlocker.injector.InjectProtocolStrengthDecode;
import sakura.kooi.dglabunlocker.injector.InjectRemoteSettingsDialog;
import sakura.kooi.dglabunlocker.injector.InjectStrengthButton;
import sakura.kooi.dglabunlocker.ui.StatusDialog;
import sakura.kooi.dglabunlocker.utils.MapUtils;

public class XposedModuleInit implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

    private final Map<Class<? extends IHookPointInjector>, Runnable> injectorClasses = MapUtils.of(
            entry(InjectRemoteSettingsDialog.class, () -> StatusDialog.remoteSettingsDialogInject = true),
            entry(InjectBluetoothServiceReceiver.class, () -> StatusDialog.bluetoothDecoderInject = true),
            entry(InjectProtocolStrengthDecode.class, () -> StatusDialog.protocolStrengthDecodeInject = true),
            entry(InjectStrengthButton.class, () -> StatusDialog.strengthButtonInject = true),
            entry(InjectControlledStrengthButton.class, () -> StatusDialog.localStrengthHandlerInject = true)
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.bjsm.dungeonlab"))
            return;
        Log.i("DgLabUnlocker", "Init Loading: found target app " + lpparam.packageName);
        try {
            Class.forName("com.wrapper.proxyapplication.WrapperProxyApplication", false, lpparam.classLoader);

            XposedHelpers.findAndHookMethod("com.wrapper.proxyapplication.WrapperProxyApplication", lpparam.classLoader,
                    "attachBaseContext", Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            ClassLoader classLoader = context.getClassLoader();
                            onAppLoaded(context, classLoader);
                        }
                    });
        } catch (ClassNotFoundException e) {
            Log.i("DgLabUnlocker", "Possible unpacked app, try hook activity directly");
            XposedHelpers.findAndHookMethod("com.bjsm.dungeonlab.global.BaseLocalApplication", lpparam.classLoader, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    ClassLoader classLoader = context.getClassLoader();
                    onAppLoaded(context, classLoader);
                }
            });
        }
    }

    private void onAppLoaded(Context context, ClassLoader classLoader) {
        Log.i("DgLabUnlocker", "Hook Loading: App loaded! Applying hooks...");
        try {
            new InjectBugReportDialog().apply(context, classLoader);
            Log.i("DgLabUnlocker", "Hook Loading: Settings dialog loaded");
            StatusDialog.moduleSettingsDialogInject = true;
        } catch (Throwable e) {
            Log.e("DgLabUnlocker", "An error occurred while InjectBugReportDialog", e);
            Toast.makeText(context, "DG-Lab Unlocker 加载失败", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            GlobalVariables.initDgLabFields(classLoader, context);
            Log.i("DgLabUnlocker", "Hook Loading: Fields lookup done");
            StatusDialog.fieldsLookup = true;
        } catch (Throwable e) {
            Log.e("DgLabUnlocker", "An error occurred while initDgLabFields()", e);
            Toast.makeText(context, "DG-Lab Unlocker 加载失败", Toast.LENGTH_LONG).show();
            return;
        }

        for (Map.Entry<Class<? extends IHookPointInjector>, Runnable> injectorClass : injectorClasses.entrySet()) {
            try {
                injectorClass.getKey().newInstance().apply(context, classLoader);
                injectorClass.getValue().run();
                Log.i("DgLabUnlocker", "Hook Loading: injected " + injectorClass.getKey().getName());
            } catch (Throwable e) {
                Log.e("DgLabUnlocker", "Could not apply " + injectorClass.getKey().getName(), e);
            }
        }

        Toast.makeText(context, "DG-Lab Unlocker 注入成功\nGithub @SakuraKoi/DgLabUnlocker", Toast.LENGTH_LONG).show();
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        GlobalVariables.modulePath = startupParam.modulePath;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!resparam.packageName.equals("com.bjsm.dungeonlab"))
            return;
        Log.i("DgLabUnlocker", "Resource Loading: found target app " + resparam.packageName);

        XModuleResources modRes = XModuleResources.createInstance(GlobalVariables.modulePath, resparam.res);
        try {
            GlobalVariables.resInjectSettingsBackground = modRes.getDrawable(R.drawable.settings_bg);
            GlobalVariables.resInjectSwitchCloseThumb = modRes.getDrawable(R.drawable.switch_close_thumb);
            GlobalVariables.resInjectSwitchOpenThumb = modRes.getDrawable(R.drawable.switch_open_thumb);
            GlobalVariables.resInjectSwitchCloseTrack = modRes.getDrawable(R.drawable.switch_close_track);
            GlobalVariables.resInjectSwitchOpenTrack = modRes.getDrawable(R.drawable.switch_open_track);
            GlobalVariables.resInjectButtonBackground = modRes.getDrawable(R.drawable.button_yellow);
        } catch (Resources.NotFoundException e) {
            Log.e("DgLabUnlocker", "settings_bg cannot be found from XModuleResources, still try inject...");
        }

        resparam.res.setReplacement("com.bjsm.dungeonlab", "string", "anquanxuzhi2", "模块设置");
        resparam.res.setReplacement("com.bjsm.dungeonlab", "string", "question_feedback", "DG-Lab Unlocker 设置");
    }
}