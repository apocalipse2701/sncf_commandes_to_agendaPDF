# Planning Commandes

Application Android personnelle qui transforme les **bulletins de commande (PDF)** en **planning mensuel**, calcule le **FISC** (jours et kilomètres) et produit un **PDF A4** du planning.

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

## ✨ Fonctions

| Onglet | Ce qu'il fait |
|---|---|
| **Planning** | Mois en grille compacte ou en page A4. Couleurs et noms des codes, heures de prise et de fin de service, « DN » automatique le lendemain d'une série de nuits, filigrane GRÈVE. Glisser le doigt pour changer de mois ; toucher un jour pour son détail (notes, jour de grève, suppression d'une ligne). |
| **Commandes** | Import d'un ou plusieurs bulletins PDF. Pour chaque jour, le bulletin le plus récent l'emporte ; les notes ajoutées à la main sont conservées. Liste des commandes importées, avec possibilité d'en retirer une. |
| **FISC** | Jours S-GIV, M-GIV, N-GIV et travaux à Givet, jours par gare (Vireux, Monthermé, Nouzonville, Deville), en AUTO uniquement, grèves déduites. Total en km, graphiques mensuels et répartition, tableau mois par mois. Distances modifiables. |
| **Réglages** | 7 thèmes de planning, éditeur des codes et couleurs (compatible `codes.txt`), copie de sauvegarde, remise à zéro protégée. |

Autres possibilités :

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

L'APK est fabriqué par GitHub Actions (`.github/workflows/apk.yml`) à chaque envoi de fichiers :

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
    │   ├── index.html                l'application (lecture des bulletins, planning, FISC, réglages)
    │   └── lib/                      pdf.js (lecture des PDF) et pdf-lib (création des PDF)
    └── res/                          icône et thèmes clair / sombre
```

Bibliothèques incluses : [pdf.js](https://github.com/mozilla/pdf.js) (Apache 2.0), [pdf-lib](https://github.com/Hopding/pdf-lib) (MIT), [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit) (Apache 2.0).

---

*Application personnelle, sans lien avec la SNCF. Les bulletins de commande restent la référence officielle.*
