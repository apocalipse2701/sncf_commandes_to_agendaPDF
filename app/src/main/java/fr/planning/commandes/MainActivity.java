package fr.planning.commandes;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Planning Commandes : l'application (page web intégrée, fichiers dans assets/)
 * affichée dans un WebView, avec une passerelle pour choisir des fichiers,
 * enregistrer un fichier et imprimer. Tout fonctionne sans Internet.
 */
public class MainActivity extends Activity {

    private static final int REQ_FICHIERS = 1;
    private static final int REQ_ENREGISTRER = 2;
    private static final String ADRESSE = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> rappelFichiers;
    private byte[] fichierEnAttente;
    private String idEnAttente;
    private String importEnAttente;
    private boolean pageChargee = false;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader chargeur = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // le planning est gardé ici (stockage de la page)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView vue, WebResourceRequest req) {
                return chargeur.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));   // lien externe : navigateur
                } catch (Exception e) {
                    // aucune appli pour ce lien
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView vue, String url) {
                pageChargee = true;
                if (importEnAttente != null) {
                    vue.evaluateJavascript(importEnAttente, null);
                    importEnAttente = null;
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vue, ValueCallback<Uri[]> rappel, FileChooserParams params) {
                if (rappelFichiers != null) rappelFichiers.onReceiveValue(null);
                rappelFichiers = rappel;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(seulementPdf(params.getAcceptTypes()) ? "application/pdf" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(i, REQ_FICHIERS);
                } catch (Exception e) {
                    rappelFichiers = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Passerelle(), "Android");
        web.loadUrl(ADRESSE);
        traiterIntent(getIntent());
    }

    private static boolean seulementPdf(String[] types) {
        if (types == null || types.length == 0) return false;
        for (String t : types) {
            String x = t == null ? "" : t.trim().toLowerCase();
            if (!x.isEmpty() && !x.equals("application/pdf") && !x.equals(".pdf")) return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requete, int resultat, Intent donnees) {
        super.onActivityResult(requete, resultat, donnees);
        if (requete == REQ_FICHIERS) {
            if (rappelFichiers == null) return;
            Uri[] choisis = null;
            if (resultat == RESULT_OK && donnees != null) {
                ClipData liste = donnees.getClipData();
                if (liste != null && liste.getItemCount() > 0) {
                    choisis = new Uri[liste.getItemCount()];
                    for (int k = 0; k < liste.getItemCount(); k++) choisis[k] = liste.getItemAt(k).getUri();
                } else if (donnees.getData() != null) {
                    choisis = new Uri[]{donnees.getData()};
                }
            }
            rappelFichiers.onReceiveValue(choisis);
            rappelFichiers = null;
        } else if (requete == REQ_ENREGISTRER) {
            boolean ok = false;
            if (resultat == RESULT_OK && donnees != null && donnees.getData() != null && fichierEnAttente != null) {
                try (OutputStream sortie = getContentResolver().openOutputStream(donnees.getData(), "wt")) {
                    if (sortie != null) {
                        sortie.write(fichierEnAttente);
                        ok = true;
                    }
                } catch (Exception e) {
                    ok = false;
                }
            }
            fichierEnAttente = null;
            repondre(idEnAttente, ok);
            idEnAttente = null;
        }
    }

    private void repondre(String id, boolean ok) {
        if (id == null) return;
        String propre = id.replaceAll("[^A-Za-z0-9]", "");
        web.evaluateJavascript("window.__retourAndroid && window.__retourAndroid('" + propre + "', " + ok + ")", null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntent(intent);
    }

    /** Bulletin PDF reçu d'une autre appli (« Ouvrir avec » ou « Partager »). */
    @SuppressWarnings("deprecation")
    private void traiterIntent(Intent intent) {
        if (intent == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            u = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Object o = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (o instanceof Uri) u = (Uri) o;
        }
        if (u == null) return;
        final Uri source = u;
        new Thread(() -> {
            try {
                String nom = nomDuFichier(source);
                byte[] octets = lire(source);
                String b64 = Base64.encodeToString(octets, Base64.NO_WRAP);
                final String js = "window.__importerAndroid && window.__importerAndroid("
                        + JSONObject.quote(nom) + ", '" + b64 + "')";
                runOnUiThread(() -> {
                    if (pageChargee) web.evaluateJavascript(js, null);
                    else importEnAttente = js;
                });
            } catch (Exception e) {
                // fichier illisible : rien à faire
            }
        }).start();
    }

    private String nomDuFichier(Uri u) {
        String nom = "bulletin.pdf";
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) nom = n;
            }
        } catch (Exception e) {
            // nom par défaut
        }
        return nom;
    }

    private byte[] lire(Uri u) throws IOException {
        try (InputStream entree = getContentResolver().openInputStream(u)) {
            if (entree == null) throw new IOException("fichier introuvable");
            ByteArrayOutputStream tampon = new ByteArrayOutputStream();
            byte[] bloc = new byte[65536];
            int n;
            while ((n = entree.read(bloc)) > 0) tampon.write(bloc, 0, n);
            return tampon.toByteArray();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.__retourArriere ? window.__retourArriere() : false", valeur -> {
            if (!"true".equals(valeur)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onPause() {
        // enregistre tout de suite ce qui est en attente
        web.evaluateJavascript("window.__sauverMaintenant && window.__sauverMaintenant()", null);
        super.onPause();
    }

    /** Fonctions appelées par la page : window.Android.enregistrer(…) et window.Android.imprimer(…). */
    private class Passerelle {
        /** Jours à afficher dans les widgets (7 jours, 7 jours sans horaires, 15 jours) : JSON préparé par la page. */
        @JavascriptInterface
        public void majWidget(final String json) {
            if (json == null || json.length() > 500000) return;
            getSharedPreferences(WidgetBase.PREFS, MODE_PRIVATE).edit().putString(WidgetBase.CLE, json).apply();
            WidgetBase.majTous(MainActivity.this);
        }

        @JavascriptInterface
        public void enregistrer(final String id, final String nom, final String type, final String base64) {
            runOnUiThread(() -> {
                byte[] octets;
                try {
                    octets = Base64.decode(base64, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    repondre(id, false);
                    return;
                }
                if (idEnAttente != null) repondre(idEnAttente, false);
                fichierEnAttente = octets;
                idEnAttente = id;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(type);
                i.putExtra(Intent.EXTRA_TITLE, nom);
                try {
                    startActivityForResult(i, REQ_ENREGISTRER);
                } catch (Exception e) {
                    fichierEnAttente = null;
                    idEnAttente = null;
                    repondre(id, false);
                }
            });
        }

        @JavascriptInterface
        public void imprimer(final String nom, final String base64) {
            runOnUiThread(() -> {
                byte[] pdf;
                try {
                    pdf = Base64.decode(base64, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    return;
                }
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) return;
                PrintAttributes attributs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .build();
                pm.print(nom, new AdaptateurPdf(nom, pdf), attributs);
            });
        }
    }

    /** Transmet un PDF déjà prêt au service d'impression d'Android. */
    private static class AdaptateurPdf extends PrintDocumentAdapter {
        private final String nom;
        private final byte[] pdf;

        AdaptateurPdf(String nom, byte[] pdf) {
            this.nom = nom;
            this.pdf = pdf;
        }

        @Override
        public void onLayout(PrintAttributes ancien, PrintAttributes nouveau, CancellationSignal annulation,
                             LayoutResultCallback rappel, Bundle extras) {
            if (annulation.isCanceled()) {
                rappel.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(nom)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build();
            rappel.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal annulation,
                            WriteResultCallback rappel) {
            try (OutputStream sortie = new FileOutputStream(destination.getFileDescriptor())) {
                sortie.write(pdf);
                rappel.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (IOException e) {
                rappel.onWriteFailed(e.getMessage());
            }
        }
    }
}
