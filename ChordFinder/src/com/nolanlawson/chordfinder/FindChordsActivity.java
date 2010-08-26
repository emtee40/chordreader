package com.nolanlawson.chordfinder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import com.nolanlawson.chordfinder.adapter.FileAdapter;
import com.nolanlawson.chordfinder.chords.regex.ChordInText;
import com.nolanlawson.chordfinder.chords.regex.ChordParser;
import com.nolanlawson.chordfinder.helper.DialogHelper;
import com.nolanlawson.chordfinder.helper.SaveFileHelper;
import com.nolanlawson.chordfinder.helper.WebPageExtractionHelper;
import com.nolanlawson.chordfinder.util.StringUtil;
import com.nolanlawson.chordfinder.util.UtilLogger;

public class FindChordsActivity extends Activity implements OnEditorActionListener, OnClickListener, TextWatcher {

	
	private static UtilLogger log = new UtilLogger(FindChordsActivity.class);
	
	private EditText searchEditText;
	private WebView webView;
	private View messageMainView, messageSecondaryView, searchingView;
	private TextView messageTextView;
	private ProgressBar progressBar;
	private ImageView infoIconImageView;
	private Button searchButton;
	
	private CustomWebViewClient client = new CustomWebViewClient();
	
	private Handler handler = new Handler(Looper.getMainLooper());
	
	private ChordWebpage chordWebpage;
	private String html = null;
	private String url = null;
	
	private String filename;
	private String chordText;
	private List<ChordInText> chordsInText;
	private int capoFret = 0;
	private int transposeHalfSteps = 0;
	
	private TextView viewingTextView;
	private BroadcastReceiver receiver;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        setContentView(R.layout.find_chords);
        
        setUpWidgets();
        
        // initially, search rather than view chords
        switchToSearchingMode();
        
        registerClickReceiver();
    }
    
    @Override
    public void onDestroy() {
    	
    	super.onDestroy();
    	unregisterReceiver(receiver);
    }

	private void registerClickReceiver() {
		receiver = new BroadcastReceiver() {
			
			@Override
			public void onReceive(Context context, Intent intent) {
				
				log.d("data string is %s", intent.getDataString());
				log.d("id is %s",intent.getData().getLastPathSegment());
				
				int id = Integer.parseInt(intent.getData().getLastPathSegment());
				
				
				
			}
		};
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addDataScheme(getPackageName());
		registerReceiver(receiver, intentFilter);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_menu, menu);
	    
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
	    switch (item.getItemId()) {
	    case R.id.menu_about:
	    	break;
	    case R.id.menu_manage_files:
	    	startDeleteSavedFilesDialog();
	    	break;
	    case R.id.menu_search_chords:
	    	switchToSearchingMode();
	    	break;
	    case R.id.menu_open_file:
	    	showOpenFileDialog();
	    	break;
	    case R.id.menu_save_chords:
	    	showSaveChordchartDialog();
	    	break;
	    case R.id.menu_transpose:
	    	createTransposeDialog();
	    	break;
	    	
	    }
	    return false;
	}


	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		MenuItem searchChordsMenuItem = menu.findItem(R.id.menu_search_chords);
		
		boolean searchMode = searchingView.getVisibility() == View.VISIBLE;
		
		// if we're already in search mode, no need to show this menu item
		searchChordsMenuItem.setVisible(!searchMode);
		searchChordsMenuItem.setEnabled(!searchMode);
		
		// if we're not in viewing mode, there's no need to show the 'save chords' menu item
		
		MenuItem saveChordsMenuItem = menu.findItem(R.id.menu_save_chords);
		
		saveChordsMenuItem.setVisible(!searchMode);
		saveChordsMenuItem.setEnabled(!searchMode);
		
		// only show transpose in viewing mode
		
		MenuItem transposeMenuItem = menu.findItem(R.id.menu_transpose);
		
		transposeMenuItem.setVisible(!searchMode);
		transposeMenuItem.setEnabled(!searchMode);
		
		return super.onPrepareOptionsMenu(menu);
	}


	private void setUpWidgets() {
		
		searchEditText = (EditText) findViewById(R.id.find_chords_edit_text);
		searchEditText.setOnEditorActionListener(this);
		searchEditText.addTextChangedListener(this);
		
		webView = (WebView) findViewById(R.id.find_chords_web_view);
		webView.setWebViewClient(client);
		
		/* JavaScript must be enabled if you want it to work, obviously */  
		webView.getSettings().setJavaScriptEnabled(true);  
		  
		/* Register a new JavaScript interface called HTMLOUT */  
		webView.addJavascriptInterface(this, "HTMLOUT");  

		progressBar = (ProgressBar) findViewById(R.id.find_chords_progress_bar);
		infoIconImageView = (ImageView) findViewById(R.id.find_chords_image_view);
		searchButton = (Button) findViewById(R.id.find_chords_search_button);
		searchButton.setOnClickListener(this);
		
		messageMainView = findViewById(R.id.find_chords_message_main_view);
		messageSecondaryView = findViewById(R.id.find_chords_message_secondary_view);
		messageSecondaryView.setOnClickListener(this);
		
		messageTextView = (TextView) findViewById(R.id.find_chords_message_text_view);
		
		viewingTextView = (TextView) findViewById(R.id.find_chords_viewing_text_view);
		
		searchingView = findViewById(R.id.find_chords_finding_view);
		
	}


	private void createTransposeDialog() {
		
		final View view = DialogHelper.createTransposeDialogView(this, capoFret, transposeHalfSteps);
		new Builder(this)
			.setTitle(R.string.transpose)
			.setCancelable(true)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					
					// grab the user's chosen values for the capo and the transposition

					View transposeView = view.findViewById(R.id.transpose_include);
					View capoView = view.findViewById(R.id.capo_include);
					
					transposeHalfSteps = DialogHelper.getSeekBarValue(transposeView) + DialogHelper.TRANSPOSE_MIN;
					capoFret = DialogHelper.getSeekBarValue(capoView) + DialogHelper.CAPO_MIN;
					
					dialog.dismiss();
					
				}
			})
			.setView(view)
			.show();
		
	}
	
	private void startDeleteSavedFilesDialog() {
		
		if (!checkSdCard()) {
			return;
		}
		
		List<CharSequence> filenames = new ArrayList<CharSequence>(SaveFileHelper.getSavedFilenames());
		
		if (filenames.isEmpty()) {
			Toast.makeText(this, R.string.no_saved_files, Toast.LENGTH_SHORT).show();
			return;			
		}
		
		final CharSequence[] filenameArray = filenames.toArray(new CharSequence[filenames.size()]);
		
		final FileAdapter dropdownAdapter = new FileAdapter(
				this, filenames, -1, true);
		
		final TextView messageTextView = new TextView(this);
		messageTextView.setText(R.string.select_files_to_delete);
		messageTextView.setPadding(3, 3, 3, 3);
		
		Builder builder = new Builder(this);
		
		builder.setTitle(R.string.manage_saved_files)
			.setCancelable(true)
			.setNegativeButton(android.R.string.cancel, null)
			.setNeutralButton(R.string.delete_all, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					boolean[] allChecked = new boolean[dropdownAdapter.getCount()];
					
					for (int i = 0; i < allChecked.length; i++) {
						allChecked[i] = true;
					}
					verifyDelete(filenameArray, allChecked, dialog);
					
				}
			})
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					
					verifyDelete(filenameArray, dropdownAdapter.getCheckedItems(), dialog);
					
				}
			})
			.setView(messageTextView)
			.setSingleChoiceItems(dropdownAdapter, 0, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dropdownAdapter.checkOrUncheck(which);
					
				}
			});
		
		builder.show();
		
	}

	protected void verifyDelete(final CharSequence[] filenameArray,
			final boolean[] checkedItems, final DialogInterface parentDialog) {
		
		Builder builder = new Builder(this);
		
		int deleteCount = 0;
		
		for (int i = 0; i < checkedItems.length; i++) {
			if (checkedItems[i]) {
				deleteCount++;
			}
		}
		
		
		final int finalDeleteCount = deleteCount;
		
		if (finalDeleteCount > 0) {
			
			builder.setTitle(R.string.delete_saved_file)
				.setCancelable(true)
				.setMessage(String.format(getText(R.string.are_you_sure).toString(), finalDeleteCount))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// ok, delete
					
					for (int i = 0; i < checkedItems.length; i++) {
						if (checkedItems[i]) {
							SaveFileHelper.deleteFile(filenameArray[i].toString());
						}
					}
					
					String toastText = String.format(getText(R.string.files_deleted).toString(), finalDeleteCount);
					Toast.makeText(FindChordsActivity.this, toastText, Toast.LENGTH_SHORT).show();
					
					dialog.dismiss();
					parentDialog.dismiss();
					
				}
			});
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.show();
		}
		
		
	}
	
	
	private void showOpenFileDialog() {
		
		if (!checkSdCard()) {
			return;
		}
		
		final List<CharSequence> filenames = new ArrayList<CharSequence>(SaveFileHelper.getSavedFilenames());
		
		if (filenames.isEmpty()) {
			Toast.makeText(this, R.string.no_saved_files, Toast.LENGTH_SHORT).show();
			return;
		}
		
		int fileToSelect = filename != null ? filenames.indexOf(filename) : -1;
		
		ArrayAdapter<CharSequence> dropdownAdapter = new FileAdapter(
				this, filenames, fileToSelect, false);
		
		Builder builder = new Builder(this);
		
		builder.setTitle(R.string.open_file)
			.setCancelable(true)
			.setSingleChoiceItems(dropdownAdapter, fileToSelect, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					String filename = filenames.get(which).toString();
					openFile(filename);
					
				}
			});
		
		builder.show();
		
	}	
	
	private void openFile(String filenameToOpen) {
		
		filename = filenameToOpen;
		
		chordText = SaveFileHelper.openFile(filename);
		
		switchToViewingMode();
	}
	
	
    public void showHTML(String html) { 
    	
    	log.d("html is %s", html);
    	
		this.html = html;

		handler.post(new Runnable() {
			
			@Override
			public void run() {
				urlAndHtmlLoaded();
				
			}
		});
		
     } 


	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		
		if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
			performSearch();
			return true;
		}
		
		
		return false;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
		
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	    	if (webView.canGoBack()) {
	    		webView.goBack();
	    		return true;
	    	}
	    } else if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getRepeatCount() == 0) {

	    	InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
	    	searchEditText.requestFocus();
	    	
	    	// show keyboard
	    	
			imm.showSoftInput(searchEditText, 0);	    		
	    	
	    	return true;
	    	

	    }

	    return super.onKeyDown(keyCode, event);
	}	

	private void performSearch() {
		
		// dismiss soft keyboard
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
		
		CharSequence searchText = searchEditText.getText();
		
		if (TextUtils.isEmpty(searchText)) {
			return;
		}
		
		searchText = searchText + " " + getText(R.string.chords_keyword);
		
		String urlEncoded = null;
		try {
			urlEncoded = URLEncoder.encode(searchText.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.e(e, "this should never happen");
		}
		
		loadUrl("http://www.google.com/search?q=" + urlEncoded);
		
	}

	private void loadUrl(String url) {
		
		log.d("url is: %s", url);
		
		webView.loadUrl(url);
		
	}


	private void getHtmlFromWebView() {
        webView.loadUrl("" +
         		"javascript:window.HTMLOUT.showHTML(" +
         		"'<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');"); 
		
	}



	public void urlLoading(String url) {
		progressBar.setVisibility(View.VISIBLE);
		infoIconImageView.setVisibility(View.GONE);
		messageTextView.setText(R.string.loading);
		messageSecondaryView.setEnabled(false);
		
	}

	public void urlLoaded(String url) {
		
		this.url = url;
		this.chordWebpage = findKnownWebpage(url);
		
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				getHtmlFromWebView();
				
			}
		});

	}
	
	private void urlAndHtmlLoaded() {
		
		progressBar.setVisibility(View.GONE);
		infoIconImageView.setVisibility(View.VISIBLE);
		webView.setVisibility(View.VISIBLE);
		
		log.d("chordWebpage is: %s", chordWebpage);
		
		
		if ((chordWebpage != null && checkHtmlOfKnownWebpage())
				|| chordWebpage == null && checkHtmlOfUnknownWebpage()) {
			messageTextView.setText(R.string.chords_found);
			messageSecondaryView.setEnabled(true);			

		} else {
			messageTextView.setText(R.string.find_chords_second_message);
			messageSecondaryView.setEnabled(false);	
		}
	}
	
	private boolean checkHtmlOfUnknownWebpage() {
		
		if (url.contains("www.google.com")) {
			return false; // skip google - we're on the search results page
		}
		
		String txt = WebPageExtractionHelper.convertHtmlToText(html);
		return ChordParser.containsLineWithChords(txt);
		
	}

	private boolean checkHtmlOfKnownWebpage() {
		
		// check to make sure that, if this is a page from a known website, we can
		// be sure that there are chords on this page
		
		String chordChart = WebPageExtractionHelper.extractChordChart(
				chordWebpage, html);
		
		log.d("chordChart is: %s", chordChart);
		
		boolean result = ChordParser.containsLineWithChords(chordChart);
		
		log.d("checkHtmlOfKnownWebpage is: %s", result);
		
		return result;

	}

	private ChordWebpage findKnownWebpage(String url) {
		
		if (url.contains("www.chordie.com")) {
			return ChordWebpage.Chordie;
		}
		return null;
	}


	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.find_chords_search_button:
			performSearch();
			break;
		case R.id.find_chords_message_secondary_view:
			analyzeHtml();
			break;
		}
		
	}	

	private void analyzeHtml() {
		
		if (chordWebpage != null) {
			// known webpage
			
			log.d("known web page: %s", chordWebpage);
			
			chordText = WebPageExtractionHelper.extractChordChart(
					chordWebpage, html);
		} else {
			// unknown webpage
			
			log.d("unknown webpage");
			
			chordText = WebPageExtractionHelper.extractLikelyChordChart(html);
			
			
			if (chordText == null) { // didn't find a good extraction, so use the entire html

				log.d("didn't find a good chord chart using the <pre> tag");
				
				chordText = WebPageExtractionHelper.convertHtmlToText(html);
			}
		}
		
		showConfirmChordchartDialog();
		
	}

	private void showConfirmChordchartDialog() {
		
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		final EditText editText = (EditText) inflater.inflate(R.layout.confirm_chords_edit_text, null);
		editText.setText(chordText);
		
		new AlertDialog.Builder(FindChordsActivity.this)  
		             .setTitle(R.string.confirm_chordchart)  
		             .setView(editText)
		             .setCancelable(true)
		             .setNegativeButton(android.R.string.cancel, null)
		             .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							chordText = editText.getText().toString();
							switchToViewingMode();
							
						}
					})  
		             .create()  
		             .show(); 
		
		//log.d(chordText);
		
	}

	protected void showSaveChordchartDialog() {
		
		if (!checkSdCard()) {
			return;
		}
		
		final EditText editText = createEditTextForFilenameSuggestingDialog();
		
		DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {

				
				if (isInvalidFilename(editText.getText())) {
					Toast.makeText(FindChordsActivity.this, R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
				} else {
					
					if (SaveFileHelper.fileExists(editText.getText().toString())) {

						new Builder(FindChordsActivity.this)
							.setCancelable(true)
							.setTitle(R.string.overwrite_file_title)
							.setMessage(R.string.overwrite_file)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									saveFile(editText.getText().toString(), chordText);
									
								}
							})
							.show();
						
						
							
					} else {
						
					}
					
					
				}
				
				
				dialog.dismiss();
				
			}
		};
		
		showFilenameSuggestingDialog(editText, onClickListener, R.string.save_file);		
		
	}
	
	private boolean isInvalidFilename(CharSequence filename) {
		
		String filenameAsString = null;
		
		return TextUtils.isEmpty(filename)
				|| (filenameAsString = filename.toString()).contains("/")
				|| filenameAsString.contains(":")
				|| filenameAsString.contains(" ")
				|| !filenameAsString.endsWith(".txt");
				
	}	

	private void saveFile(final String filename, final String filetext) {
		
		// do in background to avoid jankiness
		
		AsyncTask<Void,Void,Boolean> saveTask = new AsyncTask<Void, Void, Boolean>(){

			@Override
			protected Boolean doInBackground(Void... params) {
				return SaveFileHelper.saveFile(filetext, filename);
				
			}

			@Override
			protected void onPostExecute(Boolean successfullySavedLog) {
				
				super.onPostExecute(successfullySavedLog);
				
				if (successfullySavedLog) {
					Toast.makeText(getApplicationContext(), R.string.file_saved, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(getApplicationContext(), R.string.unable_to_save_file, Toast.LENGTH_LONG).show();
				}
			}
			
			
		};
		
		saveTask.execute((Void)null);
		
	}	
	private EditText createEditTextForFilenameSuggestingDialog() {
		
		final EditText editText = new EditText(this);
		editText.setSingleLine();
		editText.setSingleLine(true);
		editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
		editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				
				if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
					// dismiss soft keyboard
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
					return true;
				}
				
				
				return false;
			}
		});
		
		String newFilename;
		
		if (filename != null) {
			//just suggest the same filename as before
			newFilename = filename;
		} else {
			// create an initial filename to suggest to the user
			if (!TextUtils.isEmpty(searchEditText.getText())) {
				newFilename = searchEditText.getText().toString().trim().replace(' ', '_') + ".txt";
			} else {
				newFilename = "filename.txt";
			}
		}
				
		editText.setText(newFilename);
		
		// highlight everything but the .txt at the end
		editText.setSelection(0, newFilename.length() - 4);
		
		return editText;
	}
		
	private void showFilenameSuggestingDialog(EditText editText, 
			DialogInterface.OnClickListener onClickListener, int titleResId) {
		
		Builder builder = new Builder(this);
		
		builder.setTitle(titleResId)
			.setCancelable(true)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok, onClickListener)
			.setMessage(R.string.enter_filename)
			.setView(editText);
		
		builder.show();
		
	}	
	private boolean checkSdCard() {
		
		boolean result = SaveFileHelper.checkIfSdCardExists();
		
		if (!result) {
			Toast.makeText(getApplicationContext(), R.string.sd_card_not_found, Toast.LENGTH_LONG).show();
		}
		return result;
	}

	@Override
	public void afterTextChanged(Editable s) {
		searchButton.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
		
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// do nothing
		
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// do nothing
		
	}	
	

	private void analyzeChords() {
	
		chordsInText = ChordParser.findChordsInText(chordText);
		
		log.d("found %d chords", chordsInText.size());
		
		// walk backwards through each chord from finish to start
		Collections.sort(chordsInText, Collections.reverseOrder(ChordInText.sortByStartIndex()));
		
		StringBuilder stringBuilder = new StringBuilder();
		
		int lastStartIndex = chordText.length();
		
		// add a hyperlink to each chord
		for (int i = 0; i < chordsInText.size(); i++) {
			ChordInText chordInText = chordsInText.get(i);
			
			stringBuilder.insert(0, htmlEscape(chordText.substring(chordInText.getEndIndex(), lastStartIndex)));
			
			stringBuilder.insert(0,  "</a>");
			
			stringBuilder.insert(0, chordInText.getChord().toPrintableString());
			
			// uri to point back to our broadcast receiver
			int normalIndex = chordsInText.size() - i - 1;
			Uri uri = Uri.withAppendedPath(Uri.parse(getPackageName() + "://index"), String.valueOf(normalIndex));
			
			stringBuilder.insert(0, "<a href=\"" + uri.toString() + "\">");
	
			
			lastStartIndex = chordInText.getStartIndex();
		}
		
		
		
		// insert the beginning of the text last
		stringBuilder.insert(0, htmlEscape(chordText.substring(0, lastStartIndex)));

		viewingTextView.setText(Html.fromHtml(stringBuilder.toString()));
		viewingTextView.setLinkTextColor(ColorStateList.valueOf(getResources().getColor(R.color.linkColorBlue)));
		
		
	}



	private Object htmlEscape(String str) {
		return StringUtil.replace(StringUtil.replace(TextUtils.htmlEncode(str), "\n", "<br/>")," ","&nbsp;");
	}



	private void switchToViewingMode() {
		
		searchingView.setVisibility(View.GONE);
		viewingTextView.setVisibility(View.VISIBLE);
		
		viewingTextView.setMovementMethod(LinkMovementMethod.getInstance());
		viewingTextView.setText(chordText);
		
		analyzeChords();
		
		
	}
	
	private void switchToSearchingMode() {
		
		resetData();
		
		searchingView.setVisibility(View.VISIBLE);
		viewingTextView.setVisibility(View.GONE);
	}

	private void resetData() {
		
		chordText = null;
		filename = null;
		chordsInText = null;
		searchEditText.setText(null);
		capoFret = 0;
		transposeHalfSteps = 0;
		
	}

	private class CustomWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, final String url) {
			handler.post(new Runnable() {
				
				@Override
				public void run() {
					loadUrl(url);
					
				}
			});
			
			return true;
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			urlLoaded(url);
			

			
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
			
			urlLoading(url);
			
		}
		
		
		
		
		
	}

}