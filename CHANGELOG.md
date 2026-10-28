# Journal des versions

Numéro de version : **MAJEUR.MINEUR.CORRECTIF**
- **CORRECTIF** (2.0.1) : correction, petit ajustement, sans nouvelle fonction ;
- **MINEUR** (2.1.0) : nouvelle fonction ou changement visible ;
- **MAJEUR** (3.0.0) : grand changement (fonctionnement, fichiers, versions de l'application).

Chaque version existe pour le téléphone (`PlanningCommandes.apk` manuelle, `PlanningCommandes-mail.apk`, et depuis 2.9.0 `PlanningCommandes-lecture.apk` pour un proche) et pour Windows (`PlanningPDF.exe` manuelle, `PlanningPDF-mail.exe`). Le « numéro de fabrication » (N) est celui de GitHub ; chaque fabrication est gardée sur la page Releases (« Version X.Y.Z (fabrication N) »).

## 2.9.3 — 28 octobre 2026

**Partage du planning : dépôt refusé par le kDrive (correction, Android)**
- À l'enregistrement, le partage échouait avec « accès refusé par le kDrive pour dépôt du planning ». Quand le dossier n'existe pas encore, kDrive répond « refusé » au lieu de « introuvable » : l'application ne créait donc pas le dossier.
- Elle le crée maintenant dans ce cas aussi (« Private », puis « Planning partagé »), puis dépose le planning.
- Si le kDrive refuse encore, le message dit pourquoi :
  - le dossier de départ n'existe pas : la liste des dossiers présents à la racine du kDrive est affichée, avec le chemin à indiquer ;
  - tout accès est refusé : l'offre kDrive n'inclut sans doute pas le WebDAV, ou l'identifiant n'est pas l'adresse mail du compte.
- Même aide sur le téléphone en lecture.
- Programme PC : aucun changement.

## 2.9.2 — 28 octobre 2026

**Publication : l'application « lecture » aussi dans les Releases du dépôt du code**
- `PlanningCommandes-lecture.apk` n'était publié que dans le dépôt des mises à jour (planning-commandes-maj). Il est maintenant aussi joint à chaque « Version X.Y.Z (fabrication N) » de la page Releases du dépôt du code, avec les deux autres apk.
- Aucun changement dans les applications ni dans le programme PC.

## 2.9.1 — 27 octobre 2026

**Partage du planning : mode d'emploi dans l'application (Android)**
- Réglages › Partage du planning : nouvelle rubrique dépliable « Comment faire ? », avec les étapes pas à pas.
  - Téléphone principal : offre kDrive avec WebDAV, numéro du kDrive, mot de passe d'application (à copier tout de suite), code de partage, installation de la version lecture chez le proche.
  - Version lecture : mêmes réglages et même code, notifications, widgets.
- Elle rappelle aussi : envoi quand l'application est ouverte, changement de code, dossier à créer soi-même si besoin, arrêt du partage.
- Programme PC : aucun changement.

## 2.9.0 — 27 octobre 2026

**Nouvelle application « Planning (lecture) » : votre planning à jour sur le téléphone d'un proche**
- Nouveau fichier `PlanningCommandes-lecture.apk`, à installer sur le téléphone de la personne qui veut consulter votre planning (conjoint…). Les deux autres versions ne changent pas ; celle-ci s'installe à côté sans les remplacer.
- Elle affiche le planning (mois, page A4, détail du jour, export PDF/JPEG, agenda), les widgets et les avis « Contrairement » (fond jaune et notification), sans rien pouvoir modifier : pas d'onglet Commandes, ni note, ni grève.
- Le planning vient de votre téléphone, par votre **kDrive Infomaniak** (WebDAV) : Réglages › **Partage du planning**, à régler sur les deux téléphones avec le même compte, le même dossier et le même **code de partage**.
- Votre téléphone envoie le planning quelques secondes après chaque changement (commande ajoutée, reçue par mail, note, grève…), et à l'ouverture de l'application. Le téléphone du proche le relit à l'ouverture et toutes les heures, et met ses widgets à jour.
- Le fichier déposé sur le kDrive est **chiffré** (AES-256) avec le code de partage : sans ce code, il est illisible.
- Les avis « Contrairement » se valident séparément sur chaque téléphone ; seuls ceux des 14 derniers jours sont montrés au proche.
- Programme PC : aucun changement (le partage part du téléphone).

## 2.8.4 — 27 octobre 2026

**Application Android : vos codes protégés (correction)**
- Une copie du planning faite par un programme PC sans vos codes (exe lancé hors de son dossier, voir 2.8.3) remplaçait les codes du téléphone par les codes d'exemple : le planning affichait `GIV002` au lieu de son nom. Une copie qui ne contient que les codes d'exemple ne remplace plus vos codes.
- Réglages › Codes et couleurs : si le téléphone n'a que les codes d'exemple, un avis le dit et explique comment importer les vôtres (« Importer un codes.txt », ou copie envoyée par le PC). Le menu l'indique aussi.

## 2.8.3 — 27 octobre 2026

**Programme PC : noms des codes perdus quand l'exe est lancé ailleurs (correction)**
- Un exe téléchargé et lancé depuis « Téléchargements » ne trouvait pas `codes.txt` (resté dans le dossier PlanningPDF) : le planning affichait les codes bruts (`GIV002`) au lieu de leurs noms.
- Le programme retient maintenant son dossier habituel (celui où se trouve votre `codes.txt`) et s'en sert même lancé ailleurs : codes, dossier « commande », polices.
- Si aucun dossier n'est encore connu, il demande une fois où il se trouve.
- La page de téléchargement rappelle de placer l'exe dans le dossier PlanningPDF.
- Application Android : aucun changement.

## 2.8.2 — 27 octobre 2026

**Confirmation de réception : seulement pour les commandes reçues par mail (PC et Android)**
- Le bouton « Confirmer la réception » n'apparaît plus pour un bulletin ajouté à la main (ni partagé depuis une autre application) : seulement pour une commande reçue par mail.
- Les versions manuelles (sans relevé du mail) n'ont plus cette fonction : ni bouton, ni réglage.
- Réglages › Confirmation de réception : le champ « Destinataire des bulletins ajoutés à la main » est retiré.
- PC : Fichier › Confirmer la réception… ne liste que les commandes reçues par mail ; les deux menus de confirmation n'existent que dans la version « mail ».

## 2.8.1 — 27 octobre 2026

**Application Android : correction**
- Réglages › Commandes par mail : l'adresse mail tapée s'effaçait quand on touchait le champ du mot de passe.
  - Cause : l'ouverture du clavier redimensionne la fenêtre, et l'application redessinait alors toutes les pages, réglages compris.
  - Désormais, seul le planning est redessiné : rien de ce qui est en cours de saisie ne s'efface (adresse, mot de passe, expéditeurs, message de confirmation…).
- Programme PC : aucun changement.

## 2.8.0 — 27 octobre 2026

**Mises à jour depuis un dépôt public dédié (PC et Android)**
- Les nouvelles versions sont publiées dans un dépôt GitHub **public et séparé**, `planning-commandes-maj`.
  - Il ne contient que les APK, les exe, une page de téléchargement avec des liens directs et les nouveautés.
  - Le dépôt du code peut ainsi devenir **privé**.
- L'application et le programme PC y cherchent leurs mises à jour :
  - ils affichent le **numéro de version** (2.8.0…) et les **nouveautés** avant d'installer ;
  - ils vérifient l'**empreinte SHA-256** du fichier téléchargé (fichier abîmé ou incomplet refusé).
- **Transition automatique** : cette version est aussi publiée à l'ancienne place. Les téléphones et PC déjà installés s'y mettent à jour seuls, puis regardent le nouveau dépôt.

## 2.7.0 — 26 octobre 2026

**Confirmer la réception d'une commande à l'expéditeur (PC et Android)**
- Bouton **Confirmer la réception** sur chaque commande :
  - **téléphone** : dans les résultats de l'ajout et dans « Commandes importées » ;
  - **PC** : fenêtre ouverte dès qu'une commande arrive par mail, et Fichier › Confirmer la réception d'une commande….
- Le message est préparé à partir d'un modèle réglable : Réglages › Confirmation de réception, ou sur le PC Fichier › Message de confirmation….
  - `{type}`, `{debut}`, `{fin}`, `{edition}` et `{agent}` y sont remplacés.
  - Il est relu, et modifiable, avant l'envoi.
- **Version « mail »** : envoi **direct par l'application**, sans ouvrir d'autre application.
  - Depuis la boîte réglée, avec le même mot de passe.
  - En réponse au mail reçu, donc dans le même fil de discussion.
  - Toujours chiffré : SMTP sur le port 465 en SSL, ou 587 avec STARTTLS. Sans chiffrement, rien ne part.
  - Le serveur d'envoi est rempli d'après le fournisseur (Free, Orange, SFR, Bbox, La Poste, Gmail, Yahoo, iCloud, GMX, Infomaniak) ; il est modifiable dans « Serveur de messagerie ».
- **Version manuelle** : l'application mail (ou la messagerie du PC) s'ouvre avec le message prêt.
- **Destinataire** : l'expéditeur du mail reçu (adresse de réponse). Pour un bulletin ajouté à la main, le destinataire est l'adresse réglée ou un expéditeur connu.
- La commande affiche ensuite « ✓ Réception confirmée à … le … ».

## 2.6.0 — 9 octobre 2026

**Bulletins « Contrairement » (PC et Android)** — reconnus à leur nom de fichier, comme pour le rangement
- **Avis jaune écrit en rouge** « Contrairement reçu » : période, édition, chaque jour changé (**avant → après**, toucher/cliquer ouvre le mois) et les jours ajoutés ; il reste affiché (téléphone : sur tous les onglets ; PC : au-dessus du planning) **jusqu'à « Valider »**.
- **Numéro du jour en violet** pour chaque jour modifié par rapport à la commande initiale : calendrier (compact et page A4), PDF, images JPEG, impression, programme PC et widget Android « Calendrier ». Le détail du jour dit « Modifié par un contrairement » et ce qu'il y avait avant. Une nouvelle commande ordinaire plus récente sur ce jour efface le violet.
- Un même contrairement relu (copie renommée, autre appareil) ne donne qu'un avis ; avis et jours modifiés passent dans les copies échangées PC ⇄ téléphone (un avis validé d'un côté l'est aussi de l'autre).
- Version « mail » d'Android : la notification s'intitule « Contrairement reçu » (en rouge).
- Couleurs réglables dans `regles.json` (`contrairement`).

## 2.5.0 — 7 octobre 2026

**Programme PC : export en images de plusieurs mois**
- Nouveau menu Fichier › **Exporter en images JPEG…** : on coche les mois (Tout, par année, Aucun) ; un mois → un fichier `.jpg`, plusieurs mois → un `.zip` avec une image par mois (A4 paysage, 300 dpi, thème du PDF). Mêmes noms et même forme que l'export JPEG du téléphone.
- Fenêtre **Imprimer** : boutons par année ajoutés (comme sur le téléphone).
- Application Android : aucun changement (l'export JPEG de plusieurs mois existe depuis la 2.4.0).

## 2.4.0 — 7 octobre 2026

**Application Android (et version web) : export en image**
- Le bouton **PDF** de l'onglet Planning s'appelle maintenant **Exporter** et propose deux formats : **PDF** (comme avant : enregistrer ou imprimer) ou **JPEG (image)**.
- JPEG : une image par mois coché, A4 paysage (3508 × 2480), au thème choisi ; un mois → un fichier `.jpg`, plusieurs mois → les images réunies dans un `.zip`.
- Programme PC : déjà possible par Fichier › **Exporter…** (Ctrl+E), type « Image JPEG » (mois affiché) ; plusieurs mois : voir 2.5.0.

## 2.3.1 — 7 octobre 2026

**Application Android : bulletins rangés (correction)**
- **Ouvrir le dossier** ouvrait « Téléchargements » quand le dossier `Documents/Planning Commandes` n'existait pas encore (aucun bulletin rangé). Le bouton est maintenant grisé tant qu'aucun bulletin n'est rangé, et le texte explique que le dossier est créé au premier bulletin ajouté. Avant d'ouvrir, les copies manquantes sont refaites ; si le dossier ne peut pas être créé, la raison est affichée.
- Copie dans Documents plus sûre : si Android refuse le premier moyen, l'application écrit le fichier directement (Android 11 et plus). Un échec n'est plus silencieux.
- Nouvelle liste **Voir les bulletins** dans Commandes → Bulletins rangés : les bulletins par année et par mois ; toucher un bulletin l'ouvre dans la visionneuse PDF du téléphone, sans passer par l'application Fichiers.
- Le nombre de bulletins rangés se met à jour juste après un ajout.
- Programme PC : aucun changement (il range déjà dans le dossier « commande »).

## 2.3.0 — 7 octobre 2026

**Application Android : bulletins rangés comme sur le PC**
- Chaque bulletin lu (ajouté à la main, partagé ou reçu par mail) est **copié, renommé et rangé** comme dans le dossier « commande » du PC : `Documents/Planning Commandes/2026/10 - Octobre/2026-10-06 au 2026-10-12 - Commande.pdf` (mêmes noms, « Contrairement… », « (édition du …) »). Pas de copie en double ; l'original reste en place. Visible dans l'application Fichiers, sans autorisation (Android 10 et plus).
- Onglet **Commandes → Bulletins rangés** : **Ouvrir le dossier** et **Exporter en ZIP** (tous les bulletins avec leurs dossiers, enregistrés où l'on veut).
- Les bulletins importés avant cette version n'étaient pas gardés : il faut les ajouter de nouveau pour les ranger.

**Programme PC**
- Fichier › **Exporter les bulletins en ZIP…** : le dossier « commande » en une archive, de même forme que celle du téléphone.

## 2.2.0 — 6 octobre 2026

**Application Android (et version web) : Réglages plus clairs**
- L'onglet **Réglages** n'affiche plus tout d'un coup : un menu présente les rubriques (Apparence, Codes et couleurs, Commandes par mail, Agenda du téléphone, Sauvegarde et PC, Mise à jour, Nouveau planning), chacune avec un court résumé (thème choisi, nombre de codes, relevé activé ou non, date de la dernière copie…). Toucher une rubrique l'ouvre ; la flèche ‹ ou le bouton Retour du téléphone ramène au menu.
- **Commandes par mail** rangé en deux parties, « Boîte mail » et « Expéditeurs des commandes », avec moins de texte ; boutons Contacts en icône.
- Rien ne change dans les réglages eux-mêmes. Programme PC : aucun changement (il a ses menus).

## 2.1.0 — 5 octobre 2026

**Commandes par mail (versions « mail », PC et Android)**
- Choix de la **boîte mail** dans une liste : Free, Orange / Wanadoo, SFR / Neuf, Bouygues (Bbox), La Poste, Gmail, Yahoo, iCloud, GMX, Infomaniak, ou « Autre » (serveur à indiquer). Le serveur se remplit tout seul et la boîte est reconnue d'après l'adresse. Une aide dit quel mot de passe utiliser (Gmail, Yahoo, iCloud : mot de passe d'application). Outlook / Hotmail / Live et Proton Mail sont signalés comme non pris en charge.
- **Trouver les expéditeurs dans la boîte** : l'application regarde les messages des 12 derniers mois qui portent un bulletin de commande et propose leurs expéditeurs (nombre de bulletins, date du dernier) ; il suffit de choisir les deux bons. Lecture seule : seuls la liste des pièces jointes et l'expéditeur sont lus.
- Android : bouton **Contacts** à côté de chaque expéditeur, pour choisir une adresse dans les contacts du téléphone (sans autorisation « contacts »). Le PC n'a pas de carnet d'adresses : il utilise la recherche dans la boîte.
- Versions manuelles : aucun changement.

## 2.0.4 — 5 octobre 2026

**Application Android, version « mail » seulement**
- Correction : enregistrer les réglages du relevé des mails affichait « enregistrement impossible : android.permission.ACCESS_NETWORK_STATE required for jobs with a connectivity constraint ». L'autorisation (accordée automatiquement, sans question) est ajoutée : le relevé toutes les heures peut être programmé.
- Version manuelle et programme PC : aucun changement.

## 2.0.3 — 5 octobre 2026

**Fabrication (GitHub)**
- La clé de signature reçue par le secret `KEYSTORE_BASE64` est vérifiée avant de fabriquer l'APK : les espaces et retours à la ligne collés par erreur sont ignorés, et un message clair indique si le secret est absent, incomplet (collé en partie) ou si le mot de passe est incorrect. Avant, une clé collée en partie donnait seulement « Failed to read key planning … EOFException ».
- Aucun changement dans l'application ni dans le programme PC.

## 2.0.2 — 5 octobre 2026

**Confidentialité et sécurité**
- Les vrais noms de lieux et de codes ont disparu de tout ce qui est public (codes par défaut, exemples, notices, wiki, planning fictif, captures) : lieux et codes inventés (Valmont, Bellerive, Montclair, Saint-Aubin ; codes VAL…, BLR…). Votre propre codes.txt n'est pas touché.
- Les captures du README sont refaites avec des codes fictifs (et plus jamais avec votre codes.txt).
- La clé de signature de l'APK et son mot de passe ne sont plus dans le dépôt GitHub : GitHub les reçoit par deux « secrets » (KEYSTORE_BASE64, KEYSTORE_PASSWORD). Même clé qu'avant (les mises à jour s'installent toujours par-dessus), nouveau mot de passe.

## 2.0.1 — 5 octobre 2026

**Confidentialité**
- Les aperçus des widgets « Planning » et « Prochain service » (liste des widgets d'Android) et l'image des widgets du README montraient une vraie semaine de planning (18–24 mai 2026). Ils sont redessinés à partir du planning fictif « Agent Exemple » (script `mobile/captures/apercus_widgets.py`).
- Vérification complète du dépôt GitHub, du wiki et de l'archive : aucun nom, numéro CP, adresse mail ni chemin du PC.

## 2.0.0 — 5 octobre 2026

Première version numérotée. Elle réunit tout ce qui a été ajouté depuis les fabrications 1.N :

**Deux versions de l'application**
- **Manuelle** (`PlanningCommandes.apk`, `PlanningPDF.exe`) : la version habituelle, sans mail.
- **Mail** (`PlanningCommandes-mail.apk`, `PlanningPDF-mail.exe`) : récupère les bulletins dans la boîte mail Free (IMAP, lecture seule), toutes les heures ; Android : notification « Nouvelle commande ».
- Un PDF n'est relevé que si l'expéditeur est exactement l'expéditeur 1 ou 2 et si son nom contient « bulletin de commande » ou « contrairement ».
- Mot de passe chiffré sur l'appareil (coffre de clés Android, protection Windows).

**Widgets Android**
- Nouveau widget **Calendrier** : le mois ou 15 jours (2 semaines) en grille, comme la page du PDF ; style d'origine (thème du planning) ou GrapheneOS ; en « 15 jours », intitulé du jour en grand, sans horaires ; jour J encadré ; ‹ › pour changer de période.
- **Transparence du fond** réglable pour tous les widgets (aucune, 25, 50, 75 %, totale) ; sur le Calendrier, tout le tableau devient transparent.

**Codes et couleurs**
- Couleur de fond **fixe (orange)** pour C, AH, RP, RPP, F, F0 à F9, FV et les autres repos/congés, même s'ils manquent dans codes.txt.

**Fabrication et versions**
- GitHub fabrique les 2 APK et les 2 exe à chaque envoi, et garde chaque fabrication dans une release permanente.
- « Creer l'exe (Windows).bat » fabrique aussi les 2 exe sur le PC.

## 1.N — jusqu'au 2 octobre 2026

Fabrications numérotées par GitHub (1.1, 1.2…), sans journal détaillé : lecture des bulletins, planning, PDF et impression, agenda (.ics), échange PC ↔ téléphone, widgets « Planning » et « Prochain service », apparence GrapheneOS, mise à jour automatique de l'APK et de l'exe.
