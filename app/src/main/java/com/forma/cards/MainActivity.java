package com.forma.cards;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int HOME = 0, CARDS = 1, DETAIL = 2;
    private final List<CreditCard> cards = new ArrayList<>();
    private final List<Tx> imported = new ArrayList<>();
    private int page = HOME, selected = 0, lastTab = HOME;
    private boolean lightMode;
    private FrameLayout root, pageHost;
    private int bg, surface, surface2, ink, muted, activePill;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("forma", MODE_PRIVATE);
        lightMode = prefs.getBoolean("light", false);
        loadCards(prefs);
        loadImported(prefs);
        handleSharedIntent(getIntent());
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedIntent(intent);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pageHost != null) {
            imported.clear();
            loadImported(getSharedPreferences("forma", MODE_PRIVATE));
            render();
        }
    }

    @Override public void onBackPressed() {
        if(page==DETAIL){page=CARDS;lastTab=CARDS;render();}
        else if(page==CARDS){page=HOME;lastTab=HOME;render();}
        else super.onBackPressed();
    }

    private void setPalette() {
        if (lightMode) {
            bg=0xFFF5F5F5; surface=0xFFFFFFFF; surface2=0xFFE9E9E9; ink=0xFF191919; muted=0xFF626262; activePill=0xFFBDBDBD;
        } else {
            bg=0xFF101010; surface=0xFF1B1B1B; surface2=0xFF252525; ink=0xFFF5F5F5; muted=0xFF9A9A9A; activePill=0xFF505050;
        }
    }

    private void render() {
        setPalette();
        Window w=getWindow();
        w.setStatusBarColor(bg); w.setNavigationBarColor(bg);
        if (android.os.Build.VERSION.SDK_INT >= 23) w.getDecorView().setSystemUiVisibility(lightMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        root=new FrameLayout(this); root.setBackgroundColor(bg);
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setPadding(dp(18),dp(8),dp(18),0);
        root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        shell.addView(buildHeader(),new LinearLayout.LayoutParams(-1,dp(58)));
        pageHost=new FrameLayout(this); shell.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));
        View content=page==HOME?buildHome():page==CARDS?buildCards():buildDetail();
        content.setAlpha(0f); content.setTranslationX(page==HOME?-dp(16):dp(16));
        pageHost.addView(content,new FrameLayout.LayoutParams(-1,-1));
        content.animate().alpha(1f).translationX(0).setDuration(240).start();
        root.addView(buildBottomNav(),bottomNavParams());
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
        TextView logo=text("f.",15,Color.BLACK,true); logo.setGravity(Gravity.CENTER); logo.setBackground(round(0xFFF0F0F0,dp(9))); row.addView(logo,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView brand=text("forma",18,ink,true); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1); bp.leftMargin=dp(8); row.addView(brand,bp);
        TextView theme=text(lightMode?"☾":"☼",19,ink,false); theme.setGravity(Gravity.CENTER); theme.setBackground(round(surface2,dp(22))); theme.setOnClickListener(v->{lightMode=!lightMode;getSharedPreferences("forma",MODE_PRIVATE).edit().putBoolean("light",lightMode).apply();render();});
        row.addView(theme,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView profile=text("○",18,ink,false); profile.setGravity(Gravity.CENTER); profile.setBackground(round(surface2,dp(22))); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(38),dp(38));pp.leftMargin=dp(8); row.addView(profile,pp);
        return row;
    }

    private View buildHome() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        LinearLayout body=column(); body.setPadding(0,dp(14),0,dp(112)); scroll.addView(body);
        LinearLayout greeting=new LinearLayout(this); greeting.setGravity(Gravity.BOTTOM|Gravity.CENTER_VERTICAL);
        LinearLayout greetCol=column(); addText(greetCol,"SUNDAY · OCT 25",10,muted,true); addText(greetCol,"Good morning, Julian",27,ink,true);
        greeting.addView(greetCol,new LinearLayout.LayoutParams(0,-2,1)); addText(greeting,"INR · ₹",12,muted,false); body.addView(greeting);
        sectionTitle(body,"Credit overview",cards.size()+" cards",false);
        LinearLayout stats=new LinearLayout(this); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(9); body.addView(stats,sp);
        long total=0,used=0; for(CreditCard c:cards){total+=c.limit;used+=c.used;}
        stats.addView(summaryCard("Total credit limit",money(total),"Across all cards"),new LinearLayout.LayoutParams(0,dp(94),1));
        View spacer=new View(this);stats.addView(spacer,new LinearLayout.LayoutParams(dp(9),1)); stats.addView(summaryCard("Available limit",money(total-used),"Available to use"),new LinearLayout.LayoutParams(0,dp(94),1));
        sectionTitle(body,"Recent transactions","",false);
        LinearLayout list=column(); list.setPadding(dp(14),dp(6),dp(14),dp(6)); list.setBackground(round(surface,dp(16))); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);body.addView(list,lp);
        List<Tx> recent=recentTransactions(); if(recent.isEmpty()){addText(list,"No transactions yet",13,muted,false);}else for(int i=0;i<Math.min(6,recent.size());i++){Tx tx=recent.get(i);list.addView(transactionRow(tx,true));if(i<Math.min(6,recent.size())-1)list.addView(divider());}
        return scroll;
    }

    private View buildCards() {
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);
        LinearLayout body=column();body.setPadding(0,dp(16),0,dp(112));scroll.addView(body);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);LinearLayout titleCopy=column();addText(titleCopy,"YOUR WALLET · "+cards.size()+" CARDS",10,muted,true);addText(titleCopy,"Cards",29,ink,true);titleRow.addView(titleCopy,new LinearLayout.LayoutParams(0,-2,1));TextView add=text("＋ Add",12,ink,true);add.setPadding(dp(10),dp(9),dp(10),dp(9));add.setBackground(round(surface2,dp(18)));add.setOnClickListener(v->showAddCardDialog());titleRow.addView(add);body.addView(titleRow);
        addText(body,"Add your cards to track limits, transactions and rewards",12,muted,false);
        if(cards.isEmpty()){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(34),dp(20),dp(34));empty.setBackground(round(surface,dp(18)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(22);body.addView(empty,ep);addText(empty,"Your wallet is empty",17,ink,true);TextView hint=text("Add a card to begin tracking your credit limit, spending, points and transaction alerts.",12,muted,false);hint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(8);empty.addView(hint,hp);TextView addFirst=text("Add your first card",13,ink,true);addFirst.setGravity(Gravity.CENTER);addFirst.setPadding(dp(16),dp(11),dp(16),dp(11));addFirst.setBackground(round(surface2,dp(20)));LinearLayout.LayoutParams afp=new LinearLayout.LayoutParams(-2,-2);afp.topMargin=dp(16);empty.addView(addFirst,afp);addFirst.setOnClickListener(v->showAddCardDialog());}
        for(int i=0;i<cards.size();i++){final int index=i;AspectCard face=cardFace(cards.get(i));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(i==0?18:17);body.addView(face,cp);face.setOnClickListener(v->{selected=index;page=DETAIL;lastTab=CARDS;render();});}
        return scroll;
    }

    private void showAddCardDialog() {
        LinearLayout form=column();form.setPadding(dp(18),dp(6),dp(18),dp(4));
        EditText issuer=formField(form,"Bank or issuer",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText name=formField(form,"Card name (optional)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText last=formField(form,"Last 4 digits",InputType.TYPE_CLASS_NUMBER);last.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        TextView networkLabel=text("Card network",11,muted,false);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);nlp.topMargin=dp(9);form.addView(networkLabel,nlp);
        Spinner network=new Spinner(this);String[] networks={"Visa","Mastercard","RuPay"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,networks);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);network.setAdapter(adapter);form.addView(network,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText limit=formField(form,"Total credit limit (₹)",InputType.TYPE_CLASS_NUMBER);
        EditText used=formField(form,"Currently used (₹)",InputType.TYPE_CLASS_NUMBER);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Add a card").setView(scroll).setNegativeButton("Cancel",(dialog,which)->{})
                .setPositiveButton("Add card",(dialog,which)->{
                    String bank=issuer.getText().toString().trim();String digits=last.getText().toString().trim();
                    if(bank.isEmpty()||digits.length()!=4){Toast.makeText(this,"Enter the bank name and last four digits",Toast.LENGTH_LONG).show();return;}
                    long total=parseRupees(limit.getText().toString());long current=parseRupees(used.getText().toString());
                    if(total<=0||current<0||current>total){Toast.makeText(this,"Enter a valid limit and used amount",Toast.LENGTH_LONG).show();return;}
                    String product=name.getText().toString().trim();if(product.isEmpty())product=bank+" Credit Card";
                    String scheme=network.getSelectedItem().toString();int[] colors={0xFF272727,0xFF303030,0xFF393939,0xFF20262A,0xFF2B2925,0xFF2C3030};int color=colors[cards.size()%colors.length];
                    cards.add(new CreditCard(product,bank.toUpperCase(Locale.ROOT),digits,scheme,total,current,"—",0,0,0,color,new String[0][0]));saveCards();render();Toast.makeText(this,"Card added",Toast.LENGTH_SHORT).show();
                }).show();
    }

    private EditText formField(LinearLayout form,String hint,int inputType) {
        EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(14);field.setTextColor(ink);field.setHintTextColor(muted);field.setHint(hint);field.setInputType(inputType);field.setPadding(dp(12),0,dp(12),0);field.setBackground(round(surface2,dp(10)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.topMargin=dp(9);form.addView(field,p);return field;
    }

    private long parseRupees(String raw) {
        try {String value=raw.replace(",","").replace("₹","").trim();return Long.parseLong(value);}catch(Exception ignored){return -1;}
    }

    private View buildDetail() {
        CreditCard c=cards.get(selected); ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);
        LinearLayout body=column();body.setPadding(0,dp(7),0,dp(112));scroll.addView(body);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",30,ink,false);back.setGravity(Gravity.CENTER);back.setBackground(round(surface2,dp(22)));back.setOnClickListener(v->{page=CARDS;lastTab=CARDS;render();});title.addView(back,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView label=text("Card details",16,ink,true);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);label.setGravity(Gravity.CENTER);title.addView(label,tl);title.addView(new View(this),new LinearLayout.LayoutParams(dp(40),1));body.addView(title);
        AspectCard face=cardFace(c);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(10);body.addView(face,fp);
        LinearLayout stats=new LinearLayout(this);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);stp.topMargin=dp(12);body.addView(stats,stp);stats.addView(summaryCard("Available balance",money(c.limit-c.used),"of "+money(c.limit)+" credit limit"),new LinearLayout.LayoutParams(0,dp(92),1));View gap=new View(this);stats.addView(gap,new LinearLayout.LayoutParams(dp(9),1));View due=summaryCard("Next statement",c.due,"Statement balance "+money(c.statement));stats.addView(due,new LinearLayout.LayoutParams(0,dp(92),1));
        sectionTitle(body,"Spending overview","This year",true);body.addView(spendingChart(c));
        sectionTitle(body,"Points & cashback","Estimated",false);LinearLayout rewards=new LinearLayout(this);rewards.addView(summaryCard("Points earned",String.format(Locale.US,"%,d",c.points),"This month"),new LinearLayout.LayoutParams(0,dp(90),1));View rg=new View(this);rewards.addView(rg,new LinearLayout.LayoutParams(dp(9),1));rewards.addView(summaryCard("Cashback",money(Math.round(c.spend*.005)),"Estimated this month"),new LinearLayout.LayoutParams(0,dp(90),1));body.addView(rewards);
        LinearLayout txHeader=new LinearLayout(this);txHeader.setGravity(Gravity.CENTER_VERTICAL);TextView txTitle=text("Recent transactions",18,ink,true);txHeader.addView(txTitle,new LinearLayout.LayoutParams(0,-2,1));TextView auto=text(notificationAccessEnabled()?"Auto import · On":"Auto import · Off",11,muted,true);auto.setPadding(dp(5),0,dp(5),0);auto.setOnClickListener(v->requestNotificationAccess());txHeader.addView(auto);TextView paste=text("Paste",11,muted,true);paste.setPadding(dp(5),0,0,0);paste.setOnClickListener(v->showImportDialog("SMS"));txHeader.addView(paste);LinearLayout.LayoutParams thp=new LinearLayout.LayoutParams(-1,-2);thp.topMargin=dp(18);thp.bottomMargin=dp(8);body.addView(txHeader,thp);
        LinearLayout txBox=column();txBox.setPadding(dp(14),dp(5),dp(14),dp(5));txBox.setBackground(round(surface,dp(16)));List<Tx> cardTx=transactionsFor(selected);for(int i=0;i<cardTx.size();i++){txBox.addView(transactionRow(cardTx.get(i),false));if(i<cardTx.size()-1)txBox.addView(divider());}body.addView(txBox);
        sectionTitle(body,"Statements","Fetch latest",true);LinearLayout statements=column();statements.setPadding(dp(14),dp(8),dp(14),dp(8));statements.setBackground(round(surface,dp(16)));statements.addView(statementRow("October 2026 statement","Latest · connect to fetch"));statements.addView(divider());statements.addView(statementRow("September 2026 statement","Previous statement"));body.addView(statements);
        addText(body,"Automatic import reads transaction alerts from your SMS and email notifications. Parsed transactions stay on this device; message contents are not uploaded. Android requires you to enable Notification Access.",11,muted,false);
        return scroll;
    }

    private boolean notificationAccessEnabled() {
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(enabled==null)return false;
        String mine=new ComponentName(this,AlertNotificationListener.class).flattenToString();
        for(String item:enabled.split(":"))if(mine.equals(item))return true;
        return false;
    }

    private void requestNotificationAccess() {
        if(notificationAccessEnabled()){
            new AlertDialog.Builder(this).setTitle("Automatic import is on")
                    .setMessage("Forma is processing supported SMS and email transaction notifications locally. You can revoke access in Android Notification Access settings.")
                    .setNegativeButton("Done",(dialog,which)->{})
                    .setPositiveButton("Manage access",(dialog,which)->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable automatic import")
                .setMessage("Forma will look only at notifications from supported SMS and email apps, and save transaction-shaped alerts on this device. Notification text is not uploaded. Turn on Notification Access on the next screen to continue.")
                .setNegativeButton("Cancel",(dialog,which)->{})
                .setPositiveButton("Open settings",(dialog,which)->{
                    try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch(Exception e){Toast.makeText(this,"Open Android Settings → Notification access and enable Forma",Toast.LENGTH_LONG).show();}
                }).show();
    }

    private void sectionTitle(LinearLayout parent,String title,String action,boolean clickable) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(title,17,ink,true);row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));if(action!=null&&!action.isEmpty()){TextView right=text(action,11,muted,false);if(clickable)right.setOnClickListener(v->Toast.makeText(this,"Connect your card issuer to fetch statements",Toast.LENGTH_SHORT).show());row.addView(right);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(17);p.bottomMargin=dp(1);parent.addView(row,p);
    }

    private View summaryCard(String label,String value,String sub) {
        LinearLayout box=column();box.setPadding(dp(13),dp(12),dp(12),dp(10));box.setBackground(round(surface,dp(15)));addText(box,label,11,muted,false);TextView val=text(value,19,ink,true);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(4);box.addView(val,vp);TextView small=text(sub,10,muted,false);LinearLayout.LayoutParams sm=new LinearLayout.LayoutParams(-1,-2);sm.topMargin=dp(3);box.addView(small,sm);return box;
    }

    private View spendingChart(CreditCard c) {
        LinearLayout panel=column();panel.setPadding(dp(14),dp(12),dp(14),dp(11));panel.setBackground(round(surface,dp(16)));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView left=text("Card spend by month",12,ink,false);top.addView(left,new LinearLayout.LayoutParams(0,-2,1));top.addView(text(money(c.spend),12,ink,true));panel.addView(top);
        LinearLayout bars=new LinearLayout(this);bars.setGravity(Gravity.BOTTOM);bars.setOrientation(LinearLayout.HORIZONTAL);String[] months={"J","F","M","A","M","J","J","A","S","O","N","D"};int[] heights={33,48,22,65,52,39,78,47,61,75,55,43};LinearLayout.LayoutParams barsParams=new LinearLayout.LayoutParams(-1,dp(86));barsParams.topMargin=dp(8);panel.addView(bars,barsParams);
        for(int i=0;i<12;i++){LinearLayout cell=column();cell.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);View bar=new View(this);bar.setBackground(round(lightMode?0xFF888888:0xFF777777,dp(4)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(9),dp(heights[i]));cell.addView(bar,bp);TextView m=text(months[i],8,muted,false);m.setGravity(Gravity.CENTER);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(15));cell.addView(m,mp);bars.addView(cell,new LinearLayout.LayoutParams(0,-1,1));}
        TextView foot=text(String.format(Locale.US,"%s used this year · %.1f%% of limit",money(c.spend),(c.used*100d/c.limit)),10,muted,false);LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(-1,-2);f.topMargin=dp(5);panel.addView(foot,f);return panel;
    }

    private LinearLayout statementRow(String title,String sub) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView doc=text("▤",16,ink,true);doc.setGravity(Gravity.CENTER);doc.setBackground(round(surface2,dp(8)));row.addView(doc,new LinearLayout.LayoutParams(dp(34),dp(36)));LinearLayout copy=column();copy.setPadding(dp(10),0,0,0);copy.addView(text(title,12,ink,true));TextView detail=text(sub,10,muted,false);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(3);copy.addView(detail,dp1);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView button=text("View",11,lightMode?Color.WHITE:ink,true);button.setGravity(Gravity.CENTER);button.setPadding(dp(10),dp(7),dp(10),dp(7));button.setBackground(round(lightMode?0xFF333333:surface2,dp(8)));button.setOnClickListener(v->Toast.makeText(this,"Connect your card issuer to fetch this statement",Toast.LENGTH_SHORT).show());row.addView(button);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);p.bottomMargin=dp(6);row.setLayoutParams(p);return row;
    }

    private View transactionRow(Tx tx,boolean showCard) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(11),0,dp(11));TextView icon=text(tx.icon,14,ink,true);icon.setGravity(Gravity.CENTER);icon.setBackground(round(surface2,dp(10)));row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        boolean cardKnown=tx.cardIndex>=0&&tx.cardIndex<cards.size();LinearLayout info=column();LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(10);ip.rightMargin=dp(6);info.addView(text(tx.merchant,13,ink,true));String secondary=showCard&&cardKnown?tx.date+" · "+cards.get(tx.cardIndex).name:tx.date+" · "+tx.source;TextView sub=text(secondary,10,muted,false);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(3);info.addView(sub,subp);row.addView(info,ip);
        LinearLayout amountCol=column();amountCol.setGravity(Gravity.RIGHT);amountCol.addView(text("−"+tx.amount,12,ink,true));if(showCard&&cardKnown){addText(amountCol,"Card ••"+cards.get(tx.cardIndex).last.substring(2),9,muted,false);}else{addText(amountCol,tx.source.endsWith("alert")?"Auto imported":"Imported",9,muted,false);}row.addView(amountCol,new LinearLayout.LayoutParams(-2,-2));return row;
    }

    private View divider(){View d=new View(this);d.setBackgroundColor(lightMode?0xFFEAEAEA:0x22FFFFFF);return withHeight(d,1);}
    private View withHeight(View v,int h){v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(h)));return v;}

    private AspectCard cardFace(CreditCard c) {
        AspectCard card=new AspectCard();card.setBackground(round(c.cardColor,dp(18)));card.setClipToOutline(true);card.setElevation(lightMode?0:dp(2));
        TextView bank=text(c.issuer,15,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF161616:Color.WHITE,true);bank.setLetterSpacing(.04f);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels*.53),-2,Gravity.TOP|Gravity.LEFT);bp.leftMargin=dp(16);bp.topMargin=dp(15);card.addView(bank,bp);
        TextView product=text(c.name.toUpperCase(Locale.ROOT),10,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF222222:0xFFD0D0D0,true);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);pp.leftMargin=dp(16);pp.topMargin=dp(40);card.addView(product,pp);
        TextView usedLabel=text("USED LIMIT",8,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF333333:0xFFBDBDBD,true);usedLabel.setGravity(Gravity.RIGHT);FrameLayout.LayoutParams ul=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);ul.rightMargin=dp(16);ul.topMargin=dp(14);card.addView(usedLabel,ul);
        TextView used=text(money(c.used),14,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF181818:Color.WHITE,true);FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);up.rightMargin=dp(16);up.topMargin=dp(26);card.addView(used,up);
        View chip=new View(this);chip.setBackground(round(0xFFAAAAAA,dp(5)));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(37),dp(27),Gravity.TOP|Gravity.LEFT);cp.leftMargin=dp(18);cp.topMargin=dp(78);card.addView(chip,cp);
        TextView number=text("••••   ••••   ••••   "+c.last,14,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF222222:Color.WHITE,true);number.setLetterSpacing(.12f);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);np.leftMargin=dp(17);np.bottomMargin=dp(39);card.addView(number,np);
        TextView owner=text("JULIAN DAVIS",9,c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF333333:0xFFD0D0D0,true);FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);op.leftMargin=dp(17);op.bottomMargin=dp(16);card.addView(owner,op);
        TextView network=text(c.network.equals("Mastercard")?"● ●":c.network.toUpperCase(Locale.ROOT),c.network.equals("Mastercard")?12:18,c.network.equals("VISA")?0xFF234CCB:(c.cardColor==0xFFBDBDBD||c.cardColor==0xFFD0D0D0?0xFF222222:Color.WHITE),true);FrameLayout.LayoutParams nw=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);nw.rightMargin=dp(16);nw.bottomMargin=dp(13);card.addView(network,nw);
        return card;
    }

    private View buildBottomNav() {
        FrameLayout nav=new FrameLayout(this);nav.setBackground(round(lightMode?0xFFE4E4E4:0xFF1D1D1D,dp(28)));int width=dp(252),height=dp(52),inset=dp(4);int sliderW=(width-dp(10))/2;
        View slider=new View(this);slider.setBackground(round(activePill,dp(24)));FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(sliderW,height-dp(8),Gravity.TOP|Gravity.LEFT);sl.leftMargin=inset;sl.topMargin=dp(4);nav.addView(slider,sl);
        TextView home=navLabel("Home",page==HOME);TextView card=navLabel("Cards · "+cards.size(),page!=HOME);FrameLayout.LayoutParams h=new FrameLayout.LayoutParams(width/2,height);h.leftMargin=dp(1);FrameLayout.LayoutParams c=new FrameLayout.LayoutParams(width/2,height,Gravity.RIGHT);c.rightMargin=dp(1);nav.addView(home,h);nav.addView(card,c);
        home.setOnClickListener(v->{page=HOME;lastTab=HOME;render();});card.setOnClickListener(v->{page=CARDS;lastTab=CARDS;render();});
        slider.setTranslationX(lastTab==CARDS?sliderW:0);nav.post(()->{float target=page==HOME?0:sliderW;slider.animate().translationX(target).setDuration(300).start();lastTab=page==HOME?HOME:CARDS;});
        return nav;
    }

    private TextView navLabel(String s,boolean active){TextView t=text(s,12,active?(lightMode?0xFF191919:Color.WHITE):muted,active);t.setGravity(Gravity.CENTER);return t;}
    private FrameLayout.LayoutParams bottomNavParams(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(252),dp(52),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=dp(17);return p;}

    private void showImportDialog(String source) {
        boolean email=source.equalsIgnoreCase("email");EditText input=new EditText(this);input.setHint(email?"Paste an issuer email alert":"Paste a bank SMS alert");input.setMinLines(4);input.setGravity(Gravity.TOP|Gravity.START);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout box=column();box.setPadding(dp(18),dp(4),dp(18),dp(2));TextView note=text("You can also share a message from your SMS or email app to Forma. The app does not scan your inbox.",12,muted,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.bottomMargin=dp(10);box.addView(note,np);box.addView(input,new LinearLayout.LayoutParams(-1,dp(120)));
        new AlertDialog.Builder(this).setTitle(email?"Read email alert":"Read SMS alert").setView(box).setNegativeButton("Cancel",(d,w)->{}).setPositiveButton("Import",(d,w)->parseAndAdd(input.getText().toString(),email?"Email":"SMS")).show();
    }

    private void handleSharedIntent(Intent intent) {
        if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;CharSequence payload=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(payload!=null&&!payload.toString().trim().isEmpty())parseAndAdd(payload.toString(),"Shared message");
    }

    private void parseAndAdd(String text,String source) {
        if(cards.isEmpty()){Toast.makeText(this,"Add a card before importing transactions",Toast.LENGTH_LONG).show();return;}
        Matcher amountMatch=Pattern.compile("(?i)(?:₹\\s*|INR\\s*|Rs\\.?\\s*|USD\\s*|\\$\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)").matcher(text);
        if(!amountMatch.find()){Toast.makeText(this,"Could not find a transaction amount",Toast.LENGTH_SHORT).show();return;}
        String amountRaw=amountMatch.group(1).replace(",","");double amount;try{amount=Double.parseDouble(amountRaw);}catch(Exception e){Toast.makeText(this,"Could not read that amount",Toast.LENGTH_SHORT).show();return;}
        Matcher merchantMatch=Pattern.compile("(?i)\\bmerchant\\s*[:\\-]\\s*([^\\n,;.]+)|\\bat\\s+([^\\n,;.]+?)(?=\\s+(?:on|using|with|via|from|for)\\b|[,;\\n.]|$)").matcher(text);String merchant="Card transaction";if(merchantMatch.find()){merchant=merchantMatch.group(1)!=null?merchantMatch.group(1).trim():merchantMatch.group(2).trim();}
        Matcher lastFour=Pattern.compile("(?i)(?:ending|last\\s*(?:4|four)?\\s*digits?|card\\s*(?:ending|no\\.?|number)\\s*[:#\\-]?)\\s*(\\d{4})|(?:\\*{2,}|x{2,})\\s*(\\d{4})\\b").matcher(text);int cardIndex=selected;if(lastFour.find()){String digits=lastFour.group(1)!=null?lastFour.group(1):lastFour.group(2);for(int i=0;i<cards.size();i++)if(cards.get(i).last.equals(digits)){cardIndex=i;break;}}
        Tx tx=new Tx(source.equals("Email")?"✉":"↗",merchant,"Just now",money(Math.round(amount)),source,cardIndex,System.currentTimeMillis());imported.add(0,tx);saveImported();render();Toast.makeText(this,"Added to "+cards.get(cardIndex).name,Toast.LENGTH_SHORT).show();
    }

    private List<Tx> recentTransactions() {
        List<Tx> all=new ArrayList<>(imported);for(int i=0;i<cards.size();i++){CreditCard c=cards.get(i);if(c.sample.length>0){String[] s=c.sample[0];all.add(new Tx(s[0],s[1],s[2],s[3],"SMS",i,0));}}
        Collections.sort(all,(a,b)->Long.compare(b.time,a.time));return all;
    }

    private List<Tx> transactionsFor(int cardIndex) {
        List<Tx> all=new ArrayList<>();for(Tx t:imported)if(t.cardIndex==cardIndex)all.add(t);CreditCard c=cards.get(cardIndex);for(String[] s:c.sample)all.add(new Tx(s[0],s[1],s[2],s[3],"SMS",cardIndex,0));return all;
    }

    private void loadCards(SharedPreferences prefs){try{JSONArray list=new JSONArray(prefs.getString("cards","[]"));for(int i=0;i<list.length();i++){JSONObject o=list.getJSONObject(i);cards.add(new CreditCard(o.optString("name","Credit Card"),o.optString("issuer","Bank"),o.optString("last","0000"),o.optString("network","Visa"),o.optLong("limit",0),o.optLong("used",0),o.optString("due","—"),o.optLong("statement",0),o.optLong("spend",0),o.optInt("points",0),o.optInt("color",0xFF292929),new String[0][0]));}}catch(Exception ignored){}}
    private void saveCards(){JSONArray list=new JSONArray();try{for(CreditCard c:cards){JSONObject o=new JSONObject();o.put("name",c.name);o.put("issuer",c.issuer);o.put("last",c.last);o.put("network",c.network);o.put("limit",c.limit);o.put("used",c.used);o.put("due",c.due);o.put("statement",c.statement);o.put("spend",c.spend);o.put("points",c.points);o.put("color",c.cardColor);list.put(o);}getSharedPreferences("forma",MODE_PRIVATE).edit().putString("cards",list.toString()).apply();}catch(Exception ignored){}}
    private void loadImported(SharedPreferences prefs){try{JSONArray list=new JSONArray(prefs.getString("imports","[]"));for(int i=0;i<list.length();i++){JSONObject o=list.getJSONObject(i);imported.add(new Tx(o.optString("icon","↗"),o.optString("merchant","Card transaction"),o.optString("date","Imported"),o.optString("amount","₹0"),o.optString("source","Imported"),o.optInt("card",-1),o.optLong("time",0)));}}catch(Exception ignored){}}
    private void saveImported(){JSONArray list=new JSONArray();try{for(Tx t:imported){JSONObject o=new JSONObject();o.put("icon",t.icon);o.put("merchant",t.merchant);o.put("date",t.date);o.put("amount",t.amount);o.put("source",t.source);o.put("card",t.cardIndex);o.put("time",t.time);list.put(o);}getSharedPreferences("forma",MODE_PRIVATE).edit().putString("imports",list.toString()).apply();}catch(Exception ignored){}}

    private String money(long value){NumberFormat f=NumberFormat.getCurrencyInstance(new Locale("en","IN"));f.setMaximumFractionDigits(0);f.setMinimumFractionDigits(0);return f.format(value);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void addText(LinearLayout parent,String value,float size,int color,boolean bold){TextView t=text(value,size,color,bold);parent.addView(t);}
    private TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));else t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(true);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    private static final class CreditCard {
        String name,issuer,last,network,due;long limit,used,statement,spend;int points,cardColor;String[][] sample;
        CreditCard(String name,String issuer,String last,String network,long limit,long used,String due,long statement,long spend,int points,int cardColor,String[][] sample){this.name=name;this.issuer=issuer;this.last=last;this.network=network;this.limit=limit;this.used=used;this.due=due;this.statement=statement;this.spend=spend;this.points=points;this.cardColor=cardColor;this.sample=sample;}
    }
    private static final class Tx {
        String icon,merchant,date,amount,source;int cardIndex;long time;
        Tx(String icon,String merchant,String date,String amount,String source,int cardIndex,long time){this.icon=icon;this.merchant=merchant;this.date=date;this.amount=amount;this.source=source;this.cardIndex=cardIndex;this.time=time;}
    }
    private final class AspectCard extends FrameLayout {
        AspectCard(){super(MainActivity.this);}
        @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){int width=MeasureSpec.getSize(widthMeasureSpec);int height=Math.round(width/1.586f);super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}
    }
}
