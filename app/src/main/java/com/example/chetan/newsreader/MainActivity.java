package com.example.chetan.newsreader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    static ArrayList<String> titles = new ArrayList<>();
    static ArrayList<String> content = new ArrayList<>();

    static ArrayAdapter<String> arrayAdapter;

    SQLiteDatabase articlesDataBase;

    public class DownloadTask extends AsyncTask<String,Void,String> {

        @Override
        protected String doInBackground(String... urls) {

            String result = "";
            URL url;
            HttpsURLConnection urlConnection = null;

            try{

                url = new URL(urls[0]);
                urlConnection = (HttpsURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);
                int data = reader.read();

                while(data != -1){
                    char current = (char) data;
                    result += current;
                    data = reader.read();
                }
                //result=>JSONArray of [IDs] limited to just 20 items for testing purposes

                JSONArray jsonArray = new JSONArray(result);

                int numberOfItems=10;
                if(jsonArray.length() < 10){
                    numberOfItems=jsonArray.length();
                }

                articlesDataBase.execSQL("DELETE FROM articles");

                //for all the article Ids, get the article info
                for(int i=0; i<numberOfItems; i++){
                    String articleId = jsonArray.getString(i);
                    url= new URL("https://hacker-news.firebaseio.com/v0/item/" + articleId + ".json?print=pretty");
                    urlConnection = (HttpsURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                    reader = new InputStreamReader(in);
                    data = reader.read();

                    String articleInfo = "";

                    while(data != -1){
                        char current = (char) data;
                        articleInfo += current;
                        data = reader.read();
                    }

                    //Log.i("ArticleInfo", articleInfo);
                    JSONObject jsonObject = new JSONObject(articleInfo);

                    if(!jsonObject.isNull("title") && !jsonObject.isNull("url")){
                        String articleTitle = jsonObject.getString("title");
                        String articleUrl = jsonObject.getString("url");

                        //Log.i("TITLE: ", articleTitle);
                        //Log.i("URL: ", articleUrl);

                        url = new URL(articleUrl);
                        urlConnection = (HttpsURLConnection) url.openConnection();
                        in = urlConnection.getInputStream();
                        reader = new InputStreamReader(in);
                        data = reader.read();
                        String articleContent = "";
                        while(data != -1){
                            char current = (char) data;
                            articleContent += current;
                            data = reader.read();
                        }

                        //Log.i("HTML", articleContent);

                        String sql = "INSERT INTO articles (articleID, title, content) VALUES (?, ?, ?)";
                        SQLiteStatement statement = articlesDataBase.compileStatement(sql);
                        statement.bindString(1, articleId);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);

                        statement.execute();
                    }

                }

                //Log.i("ID", result);
                return result;

            } catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            updateListView();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try{
            articlesDataBase = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
            articlesDataBase.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, articleID INTEGER, title VARCHAR, content VARCHAR)");
        } catch (Exception e){
            e.printStackTrace();
        }


        DownloadTask task = new DownloadTask();
        //executing the json filled with article IDs
        task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");

        ListView listView = findViewById(R.id.listView);
        //titles.add("Example note");
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("content", content.get(i));

                startActivity(intent);
            }
        });
        updateListView();


    }

    public void updateListView(){
        Cursor c = articlesDataBase.rawQuery("SELECT * FROM articles", null);

        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");

        if(c.moveToFirst()){
            titles.clear();
            content.clear();

            do {
                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));
            } while(c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }

    }
}
