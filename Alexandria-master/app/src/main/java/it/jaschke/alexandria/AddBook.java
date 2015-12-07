package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import it.gmariotti.cardslib.library.view.component.CardThumbnailView;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.CaptureActivityAnyOrientation;
import it.jaschke.alexandria.services.DownloadImage;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private static final String TAG = "INTENT_TO_SCAN_ACTIVITY";
    private EditText ean;
    private final int LOADER_ID = 1;
    private View rootView;
    private CardThumbnailView card;
    private ImageView categoryIcon;
    private Button scanButton;
    private TextView saveButton;
    private TextView deleteButton;

    private Intent bookIntent;

    private final String EAN_CONTENT="eanContent";
    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";

    private String mScanFormat = "Format:";
    private String mScanContents = "Contents:";



    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if(ean!=null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final Context context = getActivity();

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);

        card = (CardThumbnailView) rootView.findViewById(R.id.bookCard);
        categoryIcon = (ImageView) rootView.findViewById(R.id.categoryIcon);

        //Initialize the buttons
        saveButton = (TextView) rootView.findViewById(R.id.saveButton);
        deleteButton = (TextView) rootView.findViewById(R.id.deleteButton);
        scanButton = (Button) rootView.findViewById(R.id.scanButton);
        saveButton.setOnClickListener(this);
        deleteButton.setOnClickListener(this);
        scanButton.setOnClickListener(this);


        ean = (EditText) rootView.findViewById(R.id.ean);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //User has started typing - warn them that network unavailable
                if (!isNetworkAvailable()) {
                    //Context context = getActivity();
                    CharSequence text = "Network connection unavailable";
                    int duration = Toast.LENGTH_SHORT;

                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                    return; //Nope out
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String ean = s.toString();
                //catch isbn10 numbers
                if (ean.length() == 10 && !ean.startsWith("978")) {
                    ean = "978" + ean;
                }
                if (ean.length() < 13) {
                    clearFields();
                    return;
                }

                Log.d("afterTextChanged", "Starting book intent");

                //Once we have an ISBN, start a book intent - only if there is network
                if (isNetworkAvailable()) {
                    Intent bookIntent = new Intent(getActivity(), BookService.class);
                    bookIntent.putExtra(BookService.EAN, ean);
                    bookIntent.setAction(BookService.FETCH_BOOK);
                    getActivity().startService(bookIntent);
                    AddBook.this.restartLoader();
                }
            }
        });

        //PUT THE STUFF HERE

        if(savedInstanceState!=null){
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }

        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        switch (requestCode) {
            case IntentIntegrator.REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {

                    // Parsing bar code reader result
                    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
                    String format = result.getFormatName();
                    String content = result.getContents();
                    parseScannedBarcode(format, content);
                }
                break;
        }
    }

    /**
     * Triggers a search based on the barcode scanned
     * @param format The barcode format type
     * @param content The barcode content
     */
    private void parseScannedBarcode(String format, String content){

        //This currently immediately adds to favourites o__O
        if(format.equals("EAN_13") && content.startsWith("978") && content.length()==13){

            //Set the code
            ean.setText(content);

            //Initialize book search intent
            Intent bookIntentScan = new Intent(getActivity(), BookService.class);
            bookIntentScan.putExtra(BookService.EAN, content);
            bookIntentScan.setAction(BookService.FETCH_BOOK);
            getActivity().startService(bookIntentScan);
            AddBook.this.restartLoader();
        }
    }

    //Check network connectivity
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(ean.getText().length()==0){
            return null;
        }
        String eanStr= ean.getText().toString();
        if(eanStr.length()==10 && !eanStr.startsWith("978")){
            eanStr="978"+eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));

        //Handle case of no author on file
        if(authors != null){
            String[] authorsArr = authors.split(",");
            ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
            ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",", "\n"));
        }

        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if(Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
            rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

        //If there is a category, set the icon
        if(categories != null){
            rootView.findViewById(R.id.categoryIcon).setVisibility(View.VISIBLE);
        }

        //Show the card
        card.setVisibility(View.VISIBLE);

        String s1 = String.valueOf(saveButton.getVisibility());
        String s2 = String.valueOf(deleteButton.getVisibility());

        Log.d("AddBook: ", "Visibility: " + s1 + " + " + s2);

        saveButton.setVisibility(View.VISIBLE);
        deleteButton.setVisibility(View.VISIBLE);

    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.deleteButton).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.scanButton:
                if (!isNetworkAvailable()) {
                    CharSequence text = "Network connection unavailable";
                    int duration = Toast.LENGTH_SHORT;

                    Toast toast = Toast.makeText(this.getActivity(), text, duration);
                    toast.show();

                    return; //Nope out
                }

                //Initiate book scanner
                IntentIntegrator integrator = new IntentIntegrator(getActivity());
                IntentIntegrator fragIntegrator = integrator.forSupportFragment(AddBook.this);
                fragIntegrator.setPrompt("Scan a book barcode");
                fragIntegrator.setCaptureActivity(CaptureActivityAnyOrientation.class);
                fragIntegrator.setOrientationLocked(false);
                fragIntegrator.initiateScan();
                break;
            case R.id.saveButton:
                //ean.setText("");
                String e = ean.getText().toString();

                //Once we have an ISBN, start a book intent
                bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, e);
                bookIntent.setAction(BookService.ADD_BOOK);
                getActivity().startService(bookIntent);
                AddBook.this.restartLoader();

                //Display toast for confirmation
                Toast.makeText(this.getActivity(), "Added to Library",
                        Toast.LENGTH_LONG).show();
                break;
            case R.id.deleteButton:
                bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean.getText().toString());
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                ean.setText("");

                //Don't display the card
                card.setVisibility(View.INVISIBLE);
                break;
            default:
                break;
        }
    }
}
