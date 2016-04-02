package kvj.taskw.data;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Notification;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.util.Listeners;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kvj.taskw.App;
import kvj.taskw.R;

/**
 * Created by vorobyev on 10/4/15.
 */
public class Controller extends org.kvj.bravo7.ng.Controller {

    public static interface ToastMessageListener {

        public void onMessage(String message, boolean showLong);
    }

    protected final String executable;
    private final AccountManager accountManager;

    private final Map<String, AccountController> controllerMap = new HashMap<>();
    private final NotificationManagerCompat notificationManager;

    final Collection<String> BUILTIN_REPORTS = new ArrayList<>();

    private final Listeners<ToastMessageListener> toastListeners = new Listeners<>();

    public Controller(Context context, String name) {
        super(context, name);
        Collections.addAll(BUILTIN_REPORTS, App.BUILTIN_REPORTS);
        accountManager = AccountManager.get(context);
        executable = eabiExecutable();
        notificationManager = NotificationManagerCompat.from(context);
    }

    public File fileFromIntentUri(Intent intent) {
        if (null == intent) return null;
        if (TextUtils.isEmpty(intent.getDataString())) return null;
        if (!"file".equals(intent.getData().getScheme())) {
            logger.w("Requested Uri is not file", intent.getData().getScheme(), intent.getData());
            return null;
        }
        try {
            File file = new File(intent.getData().getPath());
            if (!file.isFile() && !file.exists()) {
                logger.w("Invalid file:", file);
                return null;
            }
            if (!file.canRead() || !file.canWrite()) {
                logger.w("Invalid file access:", file, file.canRead(), file.canWrite());
                return null;
            }
            return file;
        } catch (Exception e) {
            logger.e(e, "Error getting file:", intent.getData(), intent.getData().getPath());
        }
        return null;
    }

    public String readFile(File file) {
        StringBuilder result = new StringBuilder();
        try {
            String line;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            while ((line = br.readLine()) != null) {
                result.append(line);
                result.append('\n');
            }
            br.close();
            return result.toString();
        } catch (Exception e) {
            logger.e(e, "Error reading file", file.getAbsolutePath());
            return null;
        }
    }

    public Boolean saveFile(String fileName, String text) {
        try {
            File output = new File(fileName);
            if (!output.exists() || !output.canWrite()) {
                logger.d("Invalid file:", output);
                return false;
            }
            Writer writer = new OutputStreamWriter(new FileOutputStream(output), "utf-8");
            writer.write(text);
            writer.close();
            return true;
        } catch (Exception e) {
            logger.e(e, "Failed to write file:", fileName);
            return false;
        }
    }

    public void copyToClipboard(CharSequence text) {
        ClipData clip = ClipData.newPlainText(text, text);
        getClipboard().setPrimaryClip(clip);
        messageShort("Copied to clipboard");
    }

    private ClipboardManager getClipboard() {
        return (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public boolean createShortcut(Intent shortcutIntent, String value) {
        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, value);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                           Intent.ShortcutIconResource.fromContext(context(), R.mipmap.ic_tw_logo));
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        try {
            context().sendBroadcast(addIntent);
            messageShort("Shortcut added");
            return true;
        } catch (Exception e) {
            logger.d(e, "Failed to add shortcut");
        }
        return false;
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
        Account account = account(settings().settingsString(R.string.pref_account_id, ""));
        if (null != account) {
            return accountID(account);
        }
        // No default account
        Account[] accounts = accountManager.getAccountsByType(App.ACCOUNT_TYPE);
        if (accounts.length == 0) {
            return null;
        }
        return accountID(accounts[0]);
    }

    public boolean setDefault(String account) {
        if (!TextUtils.isEmpty(account)) {
            settings().stringSettings(R.string.pref_account_id, account);
            return true;
        }
        return false;
    }

    public Collection<Account> accounts() {
        Account[] accounts = accountManager.getAccountsByType(App.ACCOUNT_TYPE);
        List<Account> result = new ArrayList<>();
        for (Account acc : accounts) {
            result.add(acc);
        }
        return result;
    }

    public List<String> accountFolders() {
        List<String> result = new ArrayList<>();
        File[] files = context().getExternalFilesDir(null).listFiles();
        for (File file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                result.add(file.getName());
            }
        }
        Collections.sort(result, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareToIgnoreCase(rhs);
            }
        });
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

    public String accountID(Account acc) {
        String folderName = accountManager.getUserData(acc, App.ACCOUNT_FOLDER);
        if (!TextUtils.isEmpty(folderName)) return folderName;
        return acc.name;
    }

    public Account account(String id) {
        for (Account acc : accountManager.getAccountsByType(App.ACCOUNT_TYPE)) {
            if (accountID(acc).equalsIgnoreCase(id)) {
                return acc;
            }
        }
        return null;
    }

    public String createAccount(String name, String folderName) {
        if (TextUtils.isEmpty(name)) {
            return "Account name is mandatory";
        }
        try {
            if (TextUtils.isEmpty(folderName)) {
                folderName = UUID.randomUUID().toString().toLowerCase();
            }
            if (null != account(folderName)) {
                return "Duplicate account";
            }
            File folder = new File(context().getExternalFilesDir(null), folderName);
            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    logger.w("Failed to create folder", name);
                    return "Storage access error";
                }
            }
            File taskrc = new File(folder, AccountController.TASKRC);
            if (!taskrc.exists()) {
                if (!taskrc.createNewFile()) {
                    logger.w("Failed to create folder", name);
                    return "Storage access error";
                }
            }
            File dataFolder = new File(folder, AccountController.DATA_FOLDER);
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdir()) {
                    logger.w("Failed to create data folder", dataFolder.getAbsolutePath());
                    return "Storage access error";
                }
            }
            Bundle data = new Bundle();
            data.putString(App.ACCOUNT_FOLDER, folderName);
            if (!accountManager.addAccountExplicitly(new Account(name, App.ACCOUNT_TYPE), "", data)) {
                logger.w("Failed to create account", name);
                return "Account create failure";
            }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public synchronized AccountController accountController(String name) {
        return accountController(name, false);
    }

    public AccountController accountController(FormController form) {
        return accountController(form.getValue(App.KEY_ACCOUNT, String.class));
    }

    public synchronized AccountController accountController(String name, boolean reinit) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        Account acc = account(name);
        if (null == acc) {
            return null;
        }
        AccountController current = controllerMap.get(name);
        if (null == current || reinit) {
            if (null != current) {
                current.stop(); // Cancel all schedules
            }
            controllerMap.put(name, new AccountController(this, accountID(acc), acc.name));
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

    public void cancel(NotificationType type, String account) {
        notificationManager.cancel(account, type.id);
    }

    public NotificationCompat.Builder newNotification(String account) {
        NotificationCompat.Builder n = new NotificationCompat.Builder(context);
        n.setContentTitle(account);
        n.setSmallIcon(R.drawable.ic_stat_logo);
        n.setWhen(System.currentTimeMillis());
        return n;
    }

    public Listeners<ToastMessageListener> toastListeners() {
        return toastListeners;
    }

    public void toastMessage(final String message, final boolean showLong) {
        toastListeners.emit(new Listeners.ListenerEmitter<ToastMessageListener>() {
            @Override
            public boolean emit(ToastMessageListener listener) {
                listener.onMessage(message, showLong);
                return false; // One shot
            }
        });
    }
}
