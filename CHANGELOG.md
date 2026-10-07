# Journal des versions

Numéro de version : **MAJEUR.MINEUR.CORRECTIF**
- **CORRECTIF** (2.0.1) : correction, petit ajustement, sans nouvelle fonction ;
- **MINEUR** (2.1.0) : nouvelle fonction ou changement visible ;
- **MAJEUR** (3.0.0) : grand changement (fonctionnement, fichiers, versions de l'application).

Chaque version existe pour le téléphone (`PlanningCommandes.apk` manuelle, `PlanningCommandes-mail.apk`) et pour Windows (`PlanningPDF.exe` manuelle, `PlanningPDF-mail.exe`). Le « numéro de fabrication » (N) est celui de GitHub ; chaque fabrication est gardée sur la page Releases (« Version X.Y.Z (fabrication N) »).

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
