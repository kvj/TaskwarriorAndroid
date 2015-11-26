package kvj.taskw.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.bundle.StringBundleAdapter;
import org.kvj.bravo7.form.impl.widget.TransientAdapter;
import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.util.Map;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.AccountController;
import kvj.taskw.data.Controller;

public class MainActivity extends AppCompatActivity {

    Logger logger = Logger.forInstance(this);

    Controller controller = App.controller();
    private Toolbar toolbar = null;
    private DrawerLayout navigationDrawer = null;
    private NavigationView navigation = null;

    private FormController form = new FormController(null);
    private MainList list = null;
    private Runnable updateTitleAction = new Runnable() {
        @Override
        public void run() {
            if (null != toolbar) toolbar.setSubtitle(list.reportInfo().description);
        }
    };
    private FloatingActionButton addButton = null;
    private ProgressBar progressBar = null;
    private AccountController.TaskListener progressListener = null;
    private TextView accountNameDisplay = null;
    private ViewGroup header = null;
    private PopupMenu.OnMenuItemClickListener accountMenuListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_account_add:
                    controller.addAccount(MainActivity.this);
                    break;
            }
            navigationDrawer.closeDrawers();
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        toolbar = (Toolbar) findViewById(R.id.list_toolbar);
        navigationDrawer = (DrawerLayout) findViewById(R.id.list_navigation_drawer);
        navigation = (NavigationView) findViewById(R.id.list_navigation);
        header = (ViewGroup) navigation.inflateHeaderView(R.layout.item_nav_header);
        navigation.setNavigationItemSelectedListener(
            new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(MenuItem item) {
                    onNavigationMenu(item);
                    return true;
                }
            });
        list = (MainList) getSupportFragmentManager().findFragmentById(R.id.list_list_fragment);
        addButton = (FloatingActionButton) findViewById(R.id.list_add_btn);
        progressBar = (ProgressBar) findViewById(R.id.list_progress);
        accountNameDisplay = (TextView) header.findViewById(R.id.list_nav_account_name);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(getDrawerToggleDelegate().getThemeUpIndicator());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (navigationDrawer.isDrawerOpen(Gravity.LEFT)) {
                    navigationDrawer.closeDrawers();
                } else {
                    navigationDrawer.openDrawer(Gravity.LEFT);
                }
            }
        });
        header.findViewById(R.id.list_nav_menu_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAccountMenu(v);
            }
        });
        list.listener(new MainListAdapter.ItemListener() {
            @Override
            public void onEdit(JSONObject json) {
                // Start editor
                edit(json);
            }

            @Override
            public void onStatus(JSONObject json) {
                changeStatus(json);
            }

            @Override
            public void onDelete(JSONObject json) {
                doOp(String.format("Delete task '%s'?", json.optString("description")), json.optString("uuid"), "delete");
            }

            @Override
            public void onAnnotate(JSONObject json) {
                annotate(json);
            }

            @Override
            public void onDenotate(JSONObject json, JSONObject annJson) {
                String text = annJson.optString("description");
                doOp(String.format("Delete annotation '%s'?", text), json.optString("uuid"), "denotate", text);
            }

            @Override
            public void onStartStop(JSONObject json) {

            }
        });
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add();
            }
        });
        progressListener = MainActivity.setupProgressListener(this, progressBar);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_ACCOUNT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_REPORT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_QUERY);
        form.load(this, savedInstanceState);
    }

    private void annotate(JSONObject json) {
        Intent dialog = new Intent(this, AnnotationDialog.class);
        dialog.putExtra(App.KEY_ACCOUNT, form.getValue(App.KEY_ACCOUNT, String.class));
        dialog.putExtra(App.KEY_EDIT_UUID, json.optString("uuid"));
        startActivityForResult(dialog, App.ANNOTATE_REQUEST);
    }

    private void showAccountMenu(View btn) {
        PopupMenu menu = new PopupMenu(this, btn);
        menu.inflate(R.menu.menu_account);
        int index = 0;
        for (String accountName : controller.accounts()) {
            menu.getMenu().add(R.id.menu_account_list, index++, 0, accountName)
                .setOnMenuItemClickListener(newAccountMenu(accountName));
        }
        menu.setOnMenuItemClickListener(accountMenuListener);
        menu.show();
    }

    private MenuItem.OnMenuItemClickListener newAccountMenu(final String accountName) {
        return new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent listIntent = new Intent(MainActivity.this, MainActivity.class);
                listIntent.putExtra(App.KEY_ACCOUNT, accountName);
                startActivity(listIntent);
                return true;
            }
        };
    }

    private void onNavigationMenu(MenuItem item) {
        final String account = form.getValue(App.KEY_ACCOUNT);
        AccountController accountController = controller.accountController(account);
        navigationDrawer.closeDrawers();
        switch (item.getItemId()) {
            case R.id.menu_nav_reload:
                if (null != accountController) {
                    refreshAccount(account);
                }
                break;
            case R.id.menu_nav_settings:
                // Open taskrc for editing
                if (null != accountController) {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    Uri uri = Uri.parse(String.format("file://%s", accountController.taskrc().getAbsolutePath()));
                    intent.setDataAndType(uri, "text/plain");
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        logger.e(e, "Failed to edit file");
                        controller.messageLong("No suitable plain text editors found");
                    }
                }
                break;
        }
    }

    private void refreshAccount(final String account) {
        new Tasks.ActivitySimpleTask<AccountController>(this) {

            @Override
            protected AccountController doInBackground() {
                return controller.accountController(account, true); //Re-init
            }

            @Override
            public void finish(AccountController result) {
                refreshReports();
                controller.messageShort("Refreshed");
            }
        }.exec();
    }

    private void changeStatus(JSONObject json) {
        String status = json.optString("status");
        String uuid = json.optString("uuid");
        String description = json.optString("description");
        if ("pending".equalsIgnoreCase(status)) {
            // Mark as done
            doOp(String.format("Complete task '%s'?", description), uuid, "done");
        }
    }

    private void doOp(String message, final String uuid, final String op, final String... ops) {
        final String account = form.getValue(App.KEY_ACCOUNT);
        final Tasks.ActivitySimpleTask<String> task = new Tasks.ActivitySimpleTask<String>(this) {

            @Override
            protected String doInBackground() {
                if ("done".equalsIgnoreCase(op))
                    return controller.accountController(account).taskDone(uuid);
                if ("delete".equalsIgnoreCase(op))
                    return controller.accountController(account).taskDelete(uuid);
                if ("start".equalsIgnoreCase(op))
                    return controller.accountController(account).taskStart(uuid);
                if ("stop".equalsIgnoreCase(op))
                    return controller.accountController(account).taskStop(uuid);
                if ("denotate".equalsIgnoreCase(op))
                    return controller.accountController(account).taskDenotate(uuid, ops[0]);
                return "Not supported operation";
            }

            @Override
            public void finish(String result) {
                if (null != result) {
                    controller.messageLong(result);
                } else {
                    list.reload();
                }
            }
        };
        if (TextUtils.isEmpty(message)) {
            task.exec();
        } else {
            controller.question(this, message, new Runnable() {
                @Override
                public void run() {
                    task.exec();
                }
            }, null);
        }
    }

    private static AccountController.TaskListener setupProgressListener(final Activity activity, final View bar) {
        return new AccountController.TaskListener() {
            @Override
            public void onStart() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bar.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onFinish() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bar.setVisibility(View.GONE);
                    }
                });
            }
        };
    }

    private void add() {
        Intent intent = new Intent(this, EditorActivity.class);
        final String account = form.getValue(App.KEY_ACCOUNT);
        controller.accountController(account).intentForEditor(intent, null);
        startActivityForResult(intent, App.EDIT_REQUEST);
    }

    private void edit(JSONObject json) {
        Intent intent = new Intent(this, EditorActivity.class);
        final String account = form.getValue(App.KEY_ACCOUNT);
        if (controller.accountController(account).intentForEditor(intent, json.optString("uuid"))) { //
            startActivityForResult(intent, App.EDIT_REQUEST);
        } else {
            controller.messageShort("Invalid task");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        form.save(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        addButton.setEnabled(false);
        if (checkAccount()) {
            addButton.setEnabled(true);
            accountController().listeners().add(progressListener);
            accountNameDisplay.setText(form.getValue(App.KEY_ACCOUNT, String.class));
        }
    }

    @Override
    protected void onDestroy() {
        if (null != accountController()) {
            accountController().listeners().remove(progressListener);
        }
        super.onDestroy();
    }

    private AccountController accountController() {
        return controller.accountController(form.getValue(App.KEY_ACCOUNT, String.class));
    }

    private boolean checkAccount() {
        if (null != form.getValue(App.KEY_ACCOUNT)) { // Have account
            refreshReports();
            return true;
        }
        String account = controller.currentAccount();
        if (account == null) {
            // Start new account UI
            controller.addAccount(this);
            return false;
        } else {
            logger.d("Refresh account:", account);
            form.setValue(App.KEY_ACCOUNT, account);
            refreshReports();
        }
        return true;
    }

    private void refreshReports() {
        final String account = form.getValue(App.KEY_ACCOUNT);
        new Tasks.ActivitySimpleTask<Map<String, String>>(this){

            @Override
            protected Map<String, String> doInBackground() {
                return controller.accountController(account).taskReports();
            }

            @Override
            public void finish(Map<String, String> result) {
                // We're in UI thread
                MenuItem reportsMenu = navigation.getMenu().findItem(R.id.menu_nav_reports);
                reportsMenu.getSubMenu().clear();
                for (Map.Entry<String, String> entry : result.entrySet()) { // Add reports
                    addReportMenuItem(entry.getKey(), entry.getValue(), reportsMenu.getSubMenu());
                }
                if (null == form.getValue(App.KEY_QUERY)) {
                    // Report mode
                    String report = form.getValue(App.KEY_REPORT);
                    if (null == report || !result.containsKey(report)) {
                        report = result.keySet().iterator().next(); // First item
                    }
                    form.setValue(App.KEY_REPORT, report);
                }
                list.load(form, updateTitleAction);
            }

            private void addReportMenuItem(final String key, String title, SubMenu menu) {
                menu.add(title).setIcon(R.drawable.ic_action_report).setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            // Show report
                            form.setValue(App.KEY_REPORT, key);
                            form.setValue(App.KEY_QUERY, null);
                            list.load(form, updateTitleAction);
                            return false;
                        }
                    });
            }
        }.exec();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_tb_reload:
                list.reload();
                break;
            case R.id.menu_tb_sync:
                sync();
                break;
            case R.id.menu_tb_undo:
                undo();
                break;
        }
        return true;
    }

    private void undo() {
        final AccountController ac = controller.accountController(form.getValue(App.KEY_ACCOUNT, String.class));
        if (null == ac) {
            return;
        }
        new Tasks.ActivitySimpleTask<String>(this){

            @Override
            protected String doInBackground() {
                return ac.taskUndo();
            }

            @Override
            public void finish(String result) {
                if (null != result) {
                    controller.messageShort(result);
                } else {
                    list.reload();
                }
            }
        }.exec();
    }

    private void sync() {
        final String account = form.getValue(App.KEY_ACCOUNT);
        if (null != account) { // Do sync
            new Tasks.ActivitySimpleTask<String>(this){

                @Override
                protected String doInBackground() {
                    return controller.accountController(account).taskSync();
                }

                @Override
                public void finish(String result) {
                    if (null != result) { // Error
                        controller.messageShort(result);
                    } else {
                        controller.messageShort("Sync success");
                        list.reload();
                    }
                }
            }.exec();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        list.reload(); // Reload after edit
    }
}
