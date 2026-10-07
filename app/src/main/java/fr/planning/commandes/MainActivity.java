package fr.planning.commandes;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
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

import org.json.JSONArray;
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
    private static final int REQ_CONTACT = 4;
    private int contactPour = 1;              // champ « Expéditeur 1 » ou « 2 » à remplir avec le contact choisi
    private static final String ADRESSE = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> rappelFichiers;
    private byte[] fichierEnAttente;
    private String idEnAttente;
    private String importEnAttente;
    private boolean pageChargee = false;
    // couleurs d'origine des barres du téléphone (thème Android), pour revenir à l'apparence « Automatique »
    private int barreEtatOrigine, barreNavOrigine, drapeauxOrigine;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        web = new WebView(this);
        setContentView(web);
        ReleveMail.programmer(this);           // tâche de relevé à jour (annulée si désactivée ou version manuelle)
        barreEtatOrigine = getWindow().getStatusBarColor();
        barreNavOrigine = getWindow().getNavigationBarColor();
        drapeauxOrigine = getWindow().getDecorView().getSystemUiVisibility();

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
        } else if (requete == REQ_CONTACT) {
            String adr = "";
            if (resultat == RESULT_OK && donnees != null && donnees.getData() != null) {
                try (Cursor c = getContentResolver().query(donnees.getData(),
                        new String[]{android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS}, null, null, null)) {
                    if (c != null && c.moveToFirst() && c.getString(0) != null) adr = c.getString(0).trim();
                } catch (Exception e) {
                    adr = "";
                }
            }
            try {
                JSONObject r = new JSONObject();
                r.put("numero", contactPour);
                r.put("adresse", adr);
                appelerPage("__contactChoisi", r.toString());
            } catch (org.json.JSONException e) {
                // impossible
            }
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

    /** Bulletin PDF reçu d'une autre appli (« Ouvrir avec » ou « Partager »), ou jour touché dans un widget. */
    @SuppressWarnings("deprecation")
    private void traiterIntent(Intent intent) {
        if (intent == null) return;
        if (ReleveMail.ACTION_COMMANDES.equals(intent.getAction())) {          // notification « Nouvelle commande »
            final String js = "window.__commandesMail && window.__commandesMail()";
            if (pageChargee) web.evaluateJavascript(js, null);
            else importEnAttente = js;
            return;
        }
        if (WidgetBase.ACTION_JOUR.equals(intent.getAction())) {
            String jour = intent.getStringExtra(WidgetBase.EXTRA_JOUR);
            if (jour != null && jour.matches("\\d{4}-\\d{2}-\\d{2}")) {
                final String js = "window.__ouvrirJour && window.__ouvrirJour('" + jour + "')";
                if (pageChargee) web.evaluateJavascript(js, null);
                else importEnAttente = js;
            }
            return;
        }
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
    /** Exécute window.<fonction>(texte JSON) dans la page (depuis n'importe quel fil). */
    private void appelerPage(String fonction, String json) {
        final String js = "window." + fonction + " && window." + fonction + "(" + json + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private static final int REQ_NOTIFICATIONS = 3;
    private static final int REQ_STOCKAGE = 5;
    private boolean stockageDemande = false;

    /** Android 9 et moins : autorisation d'écrire dans Documents (demandée une fois par lancement). */
    private void demanderStockage() {
        if (Build.VERSION.SDK_INT >= 29 || stockageDemande) return;
        stockageDemande = true;
        runOnUiThread(() -> {
            if (checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, REQ_STOCKAGE);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requete, String[] permissions, int[] resultats) {
        super.onRequestPermissionsResult(requete, permissions, resultats);
        if (requete == REQ_STOCKAGE && resultats.length > 0 && resultats[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new Thread(() -> BulletinsRanges.synchroniserVisible(MainActivity.this)).start();   // copies laissées en attente
        }
    }

    /** Android 13 et plus : autorisation d'afficher la notification « Nouvelle commande ». */
    private void demanderNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        runOnUiThread(() -> {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATIONS);
            }
        });
    }

    private class Passerelle {
        // ---- commandes reçues par mail (ReleveMail), version « mail » seulement ----

        /** true dans PlanningCommandes-mail.apk : la page affiche « Commandes reçues par mail ». */
        @JavascriptInterface
        public boolean mailDisponible() {
            return ReleveMail.ACTIF;
        }

        /** Réglages du relevé (sans le mot de passe) : {actif, adresse, serveur, port, expediteurs, motDePasse, etat, enAttente}. */
        @JavascriptInterface
        public String mailReglages() {
            return ReleveMail.reglages(MainActivity.this).toString();
        }

        /** Enregistre les réglages ; renvoie "" ou un message d'erreur. */
        @JavascriptInterface
        public String mailEnregistrer(String json) {
            try {
                JSONObject o = new JSONObject(json);
                ReleveMail.enregistrer(MainActivity.this, o);
                if (o.optBoolean("actif", false)) demanderNotifications();
                return "";
            } catch (Exception e) {
                return "Enregistrement impossible : " + e.getMessage();
            }
        }

        /** Relève tout de suite ; la réponse arrive dans window.__retourMail({ok, texte, nouveaux}). */
        @JavascriptInterface
        public void mailRelever() {
            new Thread(() -> appelerPage("__retourMail", ReleveMail.relever(MainActivity.this).toString())).start();
        }

        // ---- bulletins rangés (Documents/Planning Commandes/AAAA/MM - Mois), toutes versions ----

        /** Range une copie du bulletin lu (nom et dossier calculés par la page, comme sur le PC).
         *  json = {dossier, nom, edition} ; renvoie {chemin} (« » si déjà rangé) ou {erreur}. */
        @JavascriptInterface
        public String rangerBulletin(String json, String base64) {
            JSONObject r = new JSONObject();
            try {
                JSONObject o = new JSONObject(json);
                byte[] pdf = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT < 29) demanderStockage();
                String chemin = BulletinsRanges.ranger(MainActivity.this, o.optString("dossier", ""),
                        o.optString("nom", "commande"), o.optString("edition", ""), pdf);
                r.put("chemin", chemin);
                if (!chemin.isEmpty() && BulletinsRanges.erreurVisible != null) r.put("visible", BulletinsRanges.erreurVisible);
            } catch (Exception e) {
                try {
                    r.put("erreur", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } catch (org.json.JSONException ignore) {
                    // impossible
                }
            }
            return r.toString();
        }

        /** {nb, chemin, dossier (existe dans Documents ?), erreur (dernière copie visible ratée)}. */
        @JavascriptInterface
        public String bulletinsInfo() {
            JSONObject r = new JSONObject();
            try {
                r.put("nb", BulletinsRanges.nombre(MainActivity.this));
                r.put("chemin", BulletinsRanges.cheminVisible());
                r.put("dossier", BulletinsRanges.visibleExiste());
                if (BulletinsRanges.erreurVisible != null) r.put("erreur", BulletinsRanges.erreurVisible);
            } catch (org.json.JSONException e) {
                // impossible
            }
            return r.toString();
        }

        /** Liste des bulletins rangés : [{rel, nom, dossier}], du plus récent au plus ancien. */
        @JavascriptInterface
        public String bulletinsListe() {
            JSONArray l = new JSONArray();
            java.io.File r = BulletinsRanges.racine(MainActivity.this);
            java.util.List<java.io.File> tous = BulletinsRanges.tous(r);
            java.util.Collections.reverse(tous);
            for (java.io.File f : tous) {
                try {
                    String rel = BulletinsRanges.relatif(r, f);
                    JSONObject o = new JSONObject();
                    o.put("rel", rel);
                    o.put("nom", f.getName().replaceAll("(?i)\\.pdf$", ""));
                    o.put("dossier", rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "");
                    l.put(o);
                } catch (org.json.JSONException e) {
                    // impossible
                }
            }
            return l.toString();
        }

        /** Ouvre un bulletin rangé dans la visionneuse PDF du téléphone ; renvoie "" ou un message. */
        @JavascriptInterface
        public String ouvrirBulletin(String rel) {
            if (BulletinsRanges.fichier(MainActivity.this, rel) == null) return "Bulletin introuvable.";
            final Uri u = BulletinsFournisseur.adresse(rel);
            runOnUiThread(() -> {
                Intent voir = new Intent(Intent.ACTION_VIEW);
                voir.setDataAndType(u, "application/pdf");
                voir.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(voir);
                } catch (Exception e) {
                    appelerPage("__dossierBulletins", "{\"erreur\":\"Aucune application ne sait ouvrir un PDF sur ce téléphone.\"}");
                }
            });
            return "";
        }

        /** Ouvre Documents/Planning Commandes dans l'application Fichiers ; renvoie "" ou un message. */
        @JavascriptInterface
        public String ouvrirDossierBulletins() {
            if (BulletinsRanges.nombre(MainActivity.this) == 0)
                return "Aucun bulletin rangé pour l'instant : le dossier sera créé au premier bulletin ajouté.";
            if (Build.VERSION.SDK_INT < 29) demanderStockage();
            new Thread(() -> {
                BulletinsRanges.synchroniserVisible(MainActivity.this);     // copies manquantes (dossier effacé…)
                final boolean existe = BulletinsRanges.visibleExiste();
                final String erreur = BulletinsRanges.erreurVisible;
                runOnUiThread(() -> {
                    if (!existe) {
                        JSONObject m = new JSONObject();
                        try {
                            m.put("erreur", "Le dossier " + BulletinsRanges.cheminVisible() + " n'a pas pu être créé"
                                    + (erreur != null ? " (" + erreur + ")" : "") + ". Les bulletins restent dans la liste ci-dessous et dans le ZIP.");
                        } catch (org.json.JSONException ignore) {
                            // impossible
                        }
                        appelerPage("__dossierBulletins", m.toString());
                        return;
                    }
                    final String id = "primary:" + android.os.Environment.DIRECTORY_DOCUMENTS + "/" + BulletinsRanges.DOSSIER_VISIBLE;
                    final Uri dossier = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id);
                    Intent voir = new Intent(Intent.ACTION_VIEW);
                    voir.setDataAndType(dossier, "vnd.android.document/directory");
                    voir.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(voir);
                        appelerPage("__dossierBulletins", "{\"ok\":true}");
                        return;
                    } catch (Exception e) {
                        // pas d'application qui ouvre un dossier : sélecteur de dossiers du système, placé dessus
                    }
                    Intent arbre = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    if (Build.VERSION.SDK_INT >= 26) arbre.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, dossier);
                    try {
                        startActivity(arbre);
                        appelerPage("__dossierBulletins", "{\"ok\":true}");
                    } catch (Exception e) {
                        appelerPage("__dossierBulletins", "{\"erreur\":\"Ouvrez l'application Fichiers : " + BulletinsRanges.cheminVisible() + "\"}");
                    }
                });
            }).start();
            return "";
        }

        /** ZIP de tous les bulletins rangés, enregistré où l'on veut (comme une copie) ; réponse par __retourAndroid(id, ok). */
        @JavascriptInterface
        public void exporterBulletinsZip(final String id, final String nom) {
            new Thread(() -> {
                byte[] zip;
                try {
                    zip = BulletinsRanges.zip(MainActivity.this);
                } catch (IOException e) {
                    runOnUiThread(() -> repondre(id, false));
                    return;
                }
                final byte[] octets = zip;
                runOnUiThread(() -> {
                    if (idEnAttente != null) repondre(idEnAttente, false);
                    fichierEnAttente = octets;
                    idEnAttente = id;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/zip");
                    i.putExtra(Intent.EXTRA_TITLE, nom);
                    try {
                        startActivityForResult(i, REQ_ENREGISTRER);
                    } catch (Exception e) {
                        fichierEnAttente = null;
                        idEnAttente = null;
                        repondre(id, false);
                    }
                });
            }).start();
        }

        /** Cherche dans la boîte qui envoie les bulletins ; réponse dans window.__expediteursTrouves({ok, texte, expediteurs}). */
        @JavascriptInterface
        public void mailChercherExpediteurs(String json) {
            new Thread(() -> {
                JSONObject o;
                try {
                    o = new JSONObject(json);
                } catch (Exception e) {
                    o = new JSONObject();
                }
                appelerPage("__expediteursTrouves", ReleveMail.chercherExpediteurs(MainActivity.this, o).toString());
            }).start();
        }

        /** Ouvre la liste des contacts (adresses mail) du téléphone ; l'adresse choisie arrive dans
         *  window.__contactChoisi(numero, adresse). Sans autorisation « contacts » : Android ne transmet que l'adresse touchée. */
        @JavascriptInterface
        public void mailChoisirContact(int numero) {
            runOnUiThread(() -> {
                contactPour = numero;
                try {
                    startActivityForResult(new Intent(Intent.ACTION_PICK,
                            android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI), REQ_CONTACT);
                } catch (android.content.ActivityNotFoundException e) {
                    appelerPage("__contactChoisi", "{\"numero\":" + numero + ",\"adresse\":\"\",\"erreur\":\"Aucune application de contacts.\"}");
                }
            });
        }

        /** PDF reçus par mail et pas encore importés : [{id, nom, b64}] (au plus 20 Mo à la fois). */
        @JavascriptInterface
        public String commandesMail() {
            JSONArray l = new JSONArray();
            long total = 0;
            for (java.io.File f : ReleveMail.enAttente(MainActivity.this)) {
                if (total + f.length() > 20L * 1024 * 1024 && l.length() > 0) break;
                try (InputStream in = new java.io.FileInputStream(f)) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] bloc = new byte[65536];
                    int n;
                    while ((n = in.read(bloc)) > 0) b.write(bloc, 0, n);
                    JSONObject o = new JSONObject();
                    o.put("id", f.getName());
                    o.put("nom", ReleveMail.nomAffiche(f.getName()));
                    o.put("b64", Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP));
                    l.put(o);
                    total += f.length();
                } catch (Exception e) {
                    // fichier illisible : ignoré
                }
            }
            return l.toString();
        }

        /** La page a importé ces PDF : on les efface (seulement des fichiers du dossier des commandes). */
        @JavascriptInterface
        public void commandesMailTraitees(String jsonIds) {
            try {
                JSONArray ids = new JSONArray(jsonIds);
                java.util.Set<String> noms = new java.util.HashSet<>();
                for (int i = 0; i < ids.length(); i++) noms.add(ids.optString(i, ""));
                for (java.io.File f : ReleveMail.enAttente(MainActivity.this)) if (noms.contains(f.getName())) f.delete();
            } catch (Exception e) {
                // rien à effacer
            }
            ReleveMail.effacerNotification(MainActivity.this);
        }

        /** Apparence « GrapheneOS » : barres d'état et de navigation à la couleur du fond (« #212121 ») ;
         *  texte vide = couleurs du thème Android d'origine. */
        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void couleursBarres(final String hex) {
            runOnUiThread(() -> {
                android.view.Window w = getWindow();
                android.view.View decor = w.getDecorView();
                if (hex == null || hex.trim().isEmpty()) {
                    w.setStatusBarColor(barreEtatOrigine);
                    w.setNavigationBarColor(barreNavOrigine);
                    decor.setSystemUiVisibility(drapeauxOrigine);
                    return;
                }
                int c;
                try {
                    c = android.graphics.Color.parseColor(hex.trim());
                } catch (IllegalArgumentException e) {
                    return;
                }
                w.setStatusBarColor(c);
                w.setNavigationBarColor(c);
                int f = decor.getSystemUiVisibility() & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) f &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility(f);                    // icônes claires sur fond sombre
            });
        }

        // ---- mise à jour de l'application (release « apk » du dépôt GitHub, voir MiseAJour) ----

        /** Numéro de la version installée (numéro de fabrication GitHub) et dépôt, en JSON. */
        @JavascriptInterface
        public String versionAppli() {
            try {
                return new JSONObject().put("version", MiseAJour.versionInstallee(MainActivity.this))
                        .put("depot", MiseAJour.depot(MainActivity.this)).toString();
            } catch (org.json.JSONException e) {
                return "{}";
            }
        }

        /** Compare avec la dernière version publiée ; réponse : window.__retourMaj({ok, installee, publiee, erreur}). */
        @JavascriptInterface
        public void verifierMaj() {
            new Thread(() -> {
                JSONObject r = new JSONObject();
                try {
                    r.put("installee", MiseAJour.versionInstallee(MainActivity.this));
                    try {
                        r.put("publiee", MiseAJour.versionPubliee(MainActivity.this));
                        r.put("ok", true);
                    } catch (java.net.UnknownHostException e) {
                        r.put("ok", false).put("erreur", "pas de connexion Internet");
                    } catch (java.net.SocketTimeoutException e) {
                        r.put("ok", false).put("erreur", "GitHub ne répond pas");
                    } catch (IOException e) {
                        r.put("ok", false).put("erreur", e.getMessage() != null ? e.getMessage() : "connexion impossible");
                    }
                } catch (org.json.JSONException e) {
                    // ne peut pas arriver
                }
                appelerPage("__retourMaj", r.toString());
            }).start();
        }

        /** Télécharge et installe la nouvelle version ; étapes : window.__etatMaj({texte, fini, erreur}). */
        @JavascriptInterface
        public void installerMaj() {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                // 1re fois : autoriser « Installer des applis inconnues » pour Planning Commandes
                runOnUiThread(() -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        // réglage introuvable : le message suffit
                    }
                });
                etat("Autorisez « Planning Commandes » à installer des applications, revenez ici puis touchez à nouveau « Installer ».", true, false);
                return;
            }
            new Thread(() -> {
                try {
                    etat("Téléchargement…", false, false);
                    java.io.File apk = MiseAJour.telecharger(MainActivity.this, t -> etat(t, false, false));
                    etat("Préparation de l'installation…", false, false);
                    MiseAJour.installer(MainActivity.this, apk);
                    etat("Confirmez l'installation dans la fenêtre d'Android. Le planning est conservé.", true, false);
                } catch (java.net.UnknownHostException e) {
                    etat("Mise à jour impossible : pas de connexion Internet.", true, true);
                } catch (Exception e) {
                    etat("Mise à jour impossible : " + (e.getMessage() != null ? e.getMessage() : "erreur inconnue") + ".", true, true);
                }
            }).start();
        }

        private void etat(String texte, boolean fini, boolean erreur) {
            try {
                appelerPage("__etatMaj", new JSONObject().put("texte", texte).put("fini", fini).put("erreur", erreur).toString());
            } catch (org.json.JSONException e) {
                // ne peut pas arriver
            }
        }

        /** Jours à afficher dans les widgets : JSON {du, jours} préparé par la page. */
        @JavascriptInterface
        public void majWidget(final String json) {
            if (json == null || json.length() > 500000) return;
            getSharedPreferences(WidgetBase.PREFS, MODE_PRIVATE).edit().putString(WidgetBase.CLE, json)
                    .putLong(WidgetBase.CLE_MAJ, System.currentTimeMillis()).apply();
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
