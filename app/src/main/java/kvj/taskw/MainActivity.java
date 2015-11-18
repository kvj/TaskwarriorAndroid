package kvj.taskw;

import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.SubMenu;

import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    Logger logger = Logger.forInstance(this);

    Controller controller = App.controller();
    private RecyclerView list = null;
    private Toolbar toolbar = null;
    private DrawerLayout navigationDrawer = null;
    private NavigationView navigation = null;

    private String account = null;
    private String report = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        list = (RecyclerView) findViewById(R.id.list_main_list);
        toolbar = (Toolbar) findViewById(R.id.list_toolbar);
        navigationDrawer = (DrawerLayout) findViewById(R.id.list_navigation_drawer);
        navigation = (NavigationView) findViewById(R.id.list_navigation);
        list.setLayoutManager(new LinearLayoutManager(this));
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(getDrawerToggleDelegate().getThemeUpIndicator());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccount();
    }

    private boolean checkAccount() {
        if (null != account) { // Have account
            return true;
        }
        account = controller.currentAccount();
        if (account == null) {
            // Start new account UI
            controller.addAccount(this);
            return false;
        } else {
            logger.d("Refresh account:", account);
            refreshReports();
        }
        return true;
    }

    private void refreshReports() {
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
            }

            private void addReportMenuItem(String key, String title, SubMenu menu) {
                menu.add(title).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // Show report
                        return true;
                    }
                });
            }
        }.exec();
    }
}
