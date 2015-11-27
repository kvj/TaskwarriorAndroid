package kvj.taskw.account;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.ViewFinder;
import org.kvj.bravo7.form.impl.widget.SpinnerIntegerAdapter;
import org.kvj.bravo7.form.impl.widget.TextViewCharSequenceAdapter;
import org.kvj.bravo7.log.Logger;

import java.util.List;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.Controller;

/**
 * Created by vorobyev on 11/17/15.
 */
public class AccountAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    Logger logger = Logger.forInstance(this);

    public static class Service extends android.app.Service {

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return new AccountAuthenticator(this).getIBinder();
        }
    }

    public AccountAuthenticator(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        logger.d("editProperties", accountType, response);
        return null;
    }

    @Override
    public Bundle addAccount(final AccountAuthenticatorResponse response, String accountType,
                             String authTokenType, String[] requiredFeatures, Bundle options)
        throws NetworkErrorException {
        logger.d("addAccount", accountType, authTokenType, options, response);
        Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, new Intent(context, AccountAddDialog.class));
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                                     Bundle options) throws NetworkErrorException {
        logger.d("confirmCredentials", account, options, response);
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                               String authTokenType, Bundle options) throws NetworkErrorException {
        logger.d("getAuthToken", account, authTokenType, options, response);
        return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return authTokenType;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                    String authTokenType, Bundle options)
        throws NetworkErrorException {
        logger.d("updateCredentials", account, authTokenType, options, response);
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                              String[] features) throws NetworkErrorException {
        logger.d("hasFeatures", account, response);
        return null;
    }

    public static class AccountAddDialog extends AppCompatActivity {

        Controller controller = App.controller();
        private View okButton;
        private AccountAuthenticatorResponse mAccountAuthenticatorResponse;
        private Bundle mResultBundle = null;
        private FormController form = new FormController(new ViewFinder.ActivityViewFinder(this));
        private List<String> folders = null;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAccountAuthenticatorResponse =
                getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

            if (mAccountAuthenticatorResponse != null) {
                mAccountAuthenticatorResponse.onRequestContinued();
            }
            setContentView(R.layout.dialog_add_account);
            form.add(new TextViewCharSequenceAdapter(R.id.add_account_input, ""), "input");
            form.add(new SpinnerIntegerAdapter(R.id.add_account_folder, 0), "folder");
            folders = controller.accountFolders();
            folders.add(0, "<<Create new>>");
            ArrayAdapter<String>
                adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, folders);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            form.getView("folder", Spinner.class).setAdapter(adapter);
            okButton = findViewById(R.id.add_account_ok_btn);
            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = form.getValue("input");
                    int folderIndex = form.getValue("folder");
                    String err = controller.createAccount(name, folderIndex > 0? folders.get(folderIndex): null);
                    if (err == null) {
                        mResultBundle = new Bundle();
                        mResultBundle.putString(AccountManager.KEY_ACCOUNT_NAME, name);
                        mResultBundle
                            .putString(AccountManager.KEY_ACCOUNT_TYPE, App.ACCOUNT_TYPE);
                        finish();
                    } else {
                        controller.messageLong(err);
                    }
                }
            });
            findViewById(R.id.add_account_cancel_btn).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
        }

        public void finish() {
            if (mAccountAuthenticatorResponse != null) {
                // send the result bundle back if set, otherwise send an error.
                if (mResultBundle != null) {
                    mAccountAuthenticatorResponse.onResult(mResultBundle);
                } else {
                    mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
                }
                mAccountAuthenticatorResponse = null;
            }
            super.finish();
        }
    }

}
