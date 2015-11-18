package kvj.taskw;

import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    Controller controller = App.controller();
    private RecyclerView list = null;
    private Toolbar toolbar = null;
    private DrawerLayout navigationDrawer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        list = (RecyclerView) findViewById(R.id.list_main_list);
        toolbar = (Toolbar) findViewById(R.id.list_toolbar);
        navigationDrawer = (DrawerLayout) findViewById(R.id.list_navigation_drawer);
        list.setLayoutManager(new LinearLayoutManager(this));
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(getDrawerToggleDelegate().getThemeUpIndicator());
    }

    @Override
    protected void onResume() {
        super.onResume();
        final String account = controller.currentAccount();
        if (account == null) {
            // Start new account UI
            controller.addAccount(this);
        }
    }
}
