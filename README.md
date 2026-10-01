# Planning Commandes

Application Android personnelle qui transforme les **bulletins de commande (PDF)** en **planning mensuel** et produit un **PDF A4** du planning.

L'application est indépendante : elle ne demande pas de compte, marche **sans Internet**, et le planning reste **dans le téléphone**.

C'est la version téléphone et tablette du programme PC *Planning PDF* : les deux lisent les bulletins de la même façon, et un PDF créé sur le téléphone se rouvre dans le programme PC.

---

## 📲 Installer sur le téléphone

1. Sur le téléphone, ouvrez la page **[Releases → dernière version](../../releases/latest)**.
2. Touchez **`PlanningCommandes.apk`** pour le télécharger.
3. Ouvrez le fichier téléchargé. Autorisez l'installation depuis cette source si Android le demande, puis touchez **Installer**.
4. Si Play Protect signale une « appli inconnue » (normal pour une appli hors Play Store) : **Plus de détails → Installer quand même**.

Configuration requise : Android 7.0 ou plus récent.

---

## 💻 Programme PC (Windows)

Le même dépôt fabrique aussi le programme PC **`PlanningPDF.exe`** (dossier `pc/`).

1. Ouvrez la page **[Releases → exe](../../releases/tag/exe)** et téléchargez **`PlanningPDF.exe`**.
2. Remplacez l'ancien `PlanningPDF.exe` dans votre dossier PlanningPDF (à côté de `codes.txt` et du dossier `commande`). Vos réglages et votre planning sont conservés.

---

## ✨ Fonctions

| Onglet | Ce qu'il fait |
|---|---|
| **Planning** | Mois en grille compacte ou en page A4. Couleurs et noms des codes, heures de prise et de fin de service, « DN » automatique le lendemain d'une série de nuits, filigrane GRÈVE, **jours fériés**. Récapitulatif du mois : **heures de service, nuits, dimanches et fériés travaillés, repos**. Toucher un jour pour son détail (notes, jour de grève, suppression d'une ligne). |
| **Commandes** | Import d'un ou plusieurs bulletins PDF. Pour chaque jour, le bulletin le plus récent l'emporte ; les notes ajoutées à la main sont conservées. **Liste des jours modifiés** par une nouvelle commande (ex. « Lun 09/03 : DISPO 08:00–16:45 → RP »). |
| **Réglages** | 7 thèmes, éditeur des codes et couleurs, **export vers l'agenda** (.ics : agenda du téléphone, Google Agenda), copie de sauvegarde et **échange avec le PC**, rappel de sauvegarde, remise à zéro protégée. |

Autres possibilités :

- **Widgets** pour l'écran d'accueil :
  - **Planning** : une ligne par jour, le service en couleur. Réglé à la pose : style d'origine ou **GrapheneOS** (toujours sombre), horaires (automatique selon la largeur / toujours / jamais), **7 ou 14 jours**. Il s'adapte à sa taille (4 jours par page s'il est peu haut, bouton **Suite ▶**). Appui long → **Réglages** pour le changer.
  - **Prochain service** (2 × 1) : le service en cours ou le prochain (« Demain · 04:35–13:38 » + M-GIV en couleur).

  Toucher un jour **l'ouvre dans l'application**. Les widgets passent au jour suivant tout seuls (même après un redémarrage ou un changement d'heure) et préviennent si les données ont plus de 3 semaines (« ⚠ Données du … : ouvrez l'appli »). Appui long sur l'écran d'accueil → **Widgets** → **Planning Commandes**.

- **Bouton PDF** : enregistrer le PDF (une page A4 paysage par mois) ou **l'imprimer** directement.
- **« Ouvrir avec » / « Partager »** un bulletin PDF depuis un mail ou l'appli Fichiers : il est importé directement.
- **Échange avec le PC** : l'application reprend un `planning.json` ou un PDF créé par le programme PC, et ses PDF contiennent les données du planning.

---

## 🔄 Mettre à jour l'application

1. Modifiez ou remplacez les fichiers du dépôt (**Add file → Upload files**), puis **Commit changes**.
2. L'onglet **Actions** fabrique automatiquement un nouvel APK (environ 5 minutes).
3. Installez-le depuis la page [Releases](../../releases/latest), par-dessus l'ancienne version. **Le planning est conservé.**

> ⚠️ Ne supprimez pas `app/planning.keystore` : c'est la clé de signature. Sans elle, une nouvelle version ne pourrait plus s'installer par-dessus l'ancienne.

---

## 💾 Données et sauvegarde

- Le planning est enregistré dans l'application, sur le téléphone. Rien n'est envoyé sur Internet.
- **Désinstaller l'application efface le planning.** Faites de temps en temps **Réglages → Sauvegarde → Enregistrer une copie (.json)**, puis « Reprendre une copie… » pour la restaurer.
- Ce dépôt ne contient que le code de l'application, aucune donnée personnelle.

---

## 🛠️ Fabrication (pour information)

L'APK et l'exe sont fabriqués par GitHub Actions (`.github/workflows/apk.yml`) à chaque envoi de fichiers.
L'exe Windows : Python 3.12 + PyInstaller à partir de `pc/planning_pdf.py` et `pc/regles.json`, publié dans la release `exe`.
L'APK :

- Gradle 8.7, Android Gradle Plugin 8.5, Java 17, `compileSdk` 34, `minSdk` 24
- le numéro de version suit le numéro de fabrication GitHub
- l'APK signé est publié dans la release `apk`

Pour une fabrication manuelle : `gradle assembleRelease`, puis récupérez `app/build/outputs/apk/release/app-release.apk`.

### Organisation du projet

```
app/
├── build.gradle                      configuration Android, signature
├── planning.keystore                 clé de signature (à conserver)
└── src/main/
    ├── AndroidManifest.xml
    ├── java/fr/planning/commandes/
    │   └── MainActivity.java         fenêtre de l'appli, choix de fichiers, enregistrement, impression
    ├── assets/
    │   ├── index.html                l'application (lecture des bulletins, planning, réglages)
    │   └── lib/                      pdf.js (lecture des PDF) et pdf-lib (création des PDF)
    └── res/                          icône et thèmes clair / sombre
pc/
├── planning_pdf.py                   programme PC
└── regles.json                       règles communes PC / mobile (codes de repos, thèmes, couleurs)
```

Bibliothèques incluses : [pdf.js](https://github.com/mozilla/pdf.js) (Apache 2.0), [pdf-lib](https://github.com/Hopding/pdf-lib) (MIT), [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit) (Apache 2.0).

---

*Application personnelle, sans lien avec la SNCF. Les bulletins de commande restent la référence officielle.*
