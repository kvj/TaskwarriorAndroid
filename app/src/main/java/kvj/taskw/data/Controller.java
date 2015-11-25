package kvj.taskw.data;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kvj.taskw.App;
import kvj.taskw.R;

/**
 * Created by vorobyev on 10/4/15.
 */
public class Controller extends org.kvj.bravo7.ng.Controller {

    protected final String executable;
    private final AccountManager accountManager;

    private final Map<String, AccountController> controllerMap = new HashMap<>();
    private final NotificationManagerCompat notificationManager;

    public Controller(Context context, String name) {
        super(context, name);
        accountManager = AccountManager.get(context);
        executable = eabiExecutable();
        notificationManager = NotificationManagerCompat.from(context);
    }

    private enum Arch {Arm7, X86};

    private String eabiExecutable() {
        Arch arch = Arch.Arm7;
        String eabi = Build.CPU_ABI;
        if (eabi.equals("x86") || eabi.equals("x86_64")) {
            arch = Arch.X86;
        }
        int rawID = -1;
        switch (arch) {
            case Arm7:
                rawID = Build.VERSION.SDK_INT >= 16? R.raw.task_arm7_16: R.raw.task_arm7;
                break;
            case X86:
                rawID = Build.VERSION.SDK_INT >= 16? R.raw.task_x86_16: R.raw.task_x86;
                break;
        }
        try {
            File file = new File(context().getFilesDir(), "task");
            InputStream rawStream = context().getResources().openRawResource(rawID);
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int bytes = 0;
            while ((bytes = rawStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytes);
            }
            outputStream.close();
            rawStream.close();
            file.setExecutable(true, true);
            return file.getAbsolutePath();
        } catch (IOException e) {
            logger.e(e, "Error preparing file");
        }
        return null;
    }

    public String currentAccount() {
        Account[] accounts = accountManager.getAccountsByType(App.ACCOUNT_TYPE);
        if (accounts.length == 0) {
            return null;
        }
        return accounts[0].name;
    }

    public Collection<String> accounts() {
        Account[] accounts = accountManager.getAccountsByType(App.ACCOUNT_TYPE);
        List<String> result = new ArrayList<>();
        for (Account acc : accounts) {
            result.add(acc.name);
        }
        return result;
    }

    public void addAccount(Activity activity) {
        logger.d("Will add new account");
        accountManager.addAccount(App.ACCOUNT_TYPE, null, null, null, activity,
          new AccountManagerCallback<Bundle>() {
              @Override
              public void run(AccountManagerFuture<Bundle> future) {
                  logger.d("Add done");
              }
          }, null);
    }

    public boolean hasAccount(String text) {
        for (Account acc : accountManager.getAccountsByType(App.ACCOUNT_TYPE)) {
            if (acc.name.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    public boolean createAccount(String name) {
        try {
            File folder = new File(context().getExternalFilesDir(null), name.toLowerCase());
            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    logger.w("Failed to create folder", name);
                    return false;
                }
            }
            File taskrc = new File(folder, AccountController.TASKRC);
            if (!taskrc.exists()) {
                if (!taskrc.createNewFile()) {
                    logger.w("Failed to create folder", name);
                    return false;
                }
            }
            File dataFolder = new File(folder, AccountController.DATA_FOLDER);
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdir()) {
                    logger.w("Failed to create data folder", dataFolder.getAbsolutePath());
                    return false;
                }
            }
            if (!accountManager.addAccountExplicitly(new Account(name, App.ACCOUNT_TYPE), "", new Bundle())) {
                logger.w("Failed to create account", name);
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized AccountController accountController(String name) {
        return accountController(name, false);
    }

    public synchronized AccountController accountController(String name, boolean reinit) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        AccountController current = controllerMap.get(name);
        if (null == current || reinit) {
            if (null != current) {
                current.stop(); // Cancel all schedules
            }
            controllerMap.put(name, new AccountController(this, name));
        }
        return controllerMap.get(name);
    }

    public enum NotificationType {
        Sync(1);

        private final int id;

        NotificationType(int id) {
            this.id = id;
        }
    }

    public void notify(NotificationType type, String account, NotificationCompat.Builder n) {
        Notification nn = n.build();
        notificationManager.notify(account, type.id, nn);
    }

    public void cancelNotification(NotificationType type, String account) {
        notificationManager.cancel(account, type.id);
    }

    public NotificationCompat.Builder newNotification(String account) {
        NotificationCompat.Builder n = new NotificationCompat.Builder(context);
        n.setContentTitle(account);
        n.setSmallIcon(R.drawable.ic_stat_logo);
        n.setWhen(System.currentTimeMillis());
        return n;
    }

}
