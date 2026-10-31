# Journal des versions

Numéro de version : **MAJEUR.MINEUR.CORRECTIF**
- **CORRECTIF** (2.0.1) : correction, petit ajustement, sans nouvelle fonction ;
- **MINEUR** (2.1.0) : nouvelle fonction ou changement visible ;
- **MAJEUR** (3.0.0) : grand changement (fonctionnement, fichiers, versions de l'application).

Chaque version existe pour le téléphone (`PlanningCommandes.apk` manuelle, `PlanningCommandes-mail.apk`, et depuis 2.9.0 `PlanningCommandes-lecture.apk` pour un proche) et pour Windows (`PlanningPDF.exe` manuelle, `PlanningPDF-mail.exe`). Le « numéro de fabrication » (N) est celui de GitHub ; chaque fabrication est gardée sur la page Releases (« Version X.Y.Z (fabrication N) »).

## 2.13.3 — 31 octobre 2026

**Version lecture : une seule ligne de boutons (Android)**
- Compact, Page A4, Aujourd'hui et Actualiser sont sur la même ligne, ce qui laisse plus de place au planning. Les boutons sont un peu plus petits ; sur les écrans étroits, l'icône d'Actualiser est masquée.

## 2.13.2 — 31 octobre 2026

**Version lecture : bouton Actualiser (Android)**
- Le bouton **Exporter** est remplacé par **Actualiser** : il vérifie tout de suite si le planning partagé a changé. Le planning se met à jour (« Planning mis à jour »), ou le message « Aucune modification du planning » s'affiche, avec l'heure de vérification et celle du dernier envoi.
- Les pastilles colorées qui résument le mois (codes, heures de service, nuits, dimanches, fériés, repos) ne sont plus affichées dans la version lecture.
- Applications manuelle et mail : inchangées.

## 2.13.1 — 31 octobre 2026

**DII archivées avec les commandes (PC et Android)**
- La DII remplie et envoyée, et le formulaire renvoyé par l'établissement (avis), sont rangés comme les bulletins de commande, dans le dossier du mois du jour déclaré :
  - `2026/11 - Novembre/2026-11-03 - DII envoyée - NOM Prénom.pdf`
  - `2026/11 - Novembre/2026-11-03 - DII avis de l'établissement.pdf` (« (incomplet) » si la partie 1 n'est pas remplie)
- Téléphone : Documents/Planning Commandes (liste « Bulletins rangés »). PC : dossier « commande ».
- Les DII sont comprises dans le ZIP des bulletins et dans la sauvegarde sur le NAS ou le kDrive (automatique si elle est activée).
- Elles ne sont pas lues comme des bulletins : le planning n'est pas modifié.
- Envoi par la messagerie du PC : le fichier à joindre est celui rangé dans le dossier « commande » (dossier ouvert).

## 2.13.0 — 31 octobre 2026

**Grève posée seulement à la réception de l'avis de l'établissement (PC et Android)**
- À l'envoi d'une DII, le filigrane GRÈVE n'est plus posé tout de suite. Nouvelle case (envoi direct) : « Filigrane GRÈVE à la réception de l'avis de l'établissement (cadre partie 1 rempli) ».
- L'application cherche dans la boîte mail le formulaire DII renvoyé par l'établissement : en pièce jointe d'une réponse, ou d'un message du destinataire.
- Elle vérifie que la **partie 1 du « Cadre réservé à l'établissement »** est remplie intégralement : n° de DII, représentant, fonction, service, nom de l'agent, date et heure de réception, mode de réception, lieu et date.
  - **Complète** : l'avis est noté et le **filigrane GRÈVE est posé** sur le jour déclaré, avec un message (notification sur le téléphone).
  - **Incomplète**, ou formulaire imprimé, scanné ou aplati : pas de grève. L'application dit ce qui manque, avec **Ouvrir l'avis reçu** et **Avis vérifié : mettre la grève** pour valider soi-même.
- Où le voir : détail du jour (téléphone et PC) et Fichier › Suivi des DII envoyées (PC).
- Envoi par l'application mail ou la messagerie du PC : la grève se pose à la main (« Jour de grève ») à la réception de l'avis.
- Le bouton « Jour de grève » reste disponible à tout moment.

## 2.12.0 — 31 octobre 2026

**Suivi des DII envoyées : l'application prévient quand l'accusé de réception arrive (PC et Android)**
- Chaque DII envoyée directement par l'application est gardée avec son identifiant de message. Pendant 30 jours, la boîte de réception est fouillée (lecture seule) pour retrouver :
  - l'**avis de remise** du serveur (ou un **échec** de remise, ou un retard) ;
  - l'**accusé de lecture** du destinataire ;
  - une **réponse** du destinataire.
- **Téléphone** :
  - recherche à l'ouverture, puis toutes les heures en arrière-plan, et sur demande ;
  - **notification** « DII du … : remise au destinataire / lue par le destinataire » ;
  - le **détail du jour** affiche l'état : envoyée le …, remise le …, lue le …, avec un bouton **Vérifier les accusés de réception**.
- **PC** :
  - recherche avec chaque relevé de la boîte (toutes les heures), et toutes les 15 min pendant l'heure qui suit un envoi ;
  - une fenêtre prévient de chaque nouvel accusé ;
  - le détail du jour affiche l'état, et Fichier › **Suivi des DII envoyées…** liste les envois avec **Vérifier maintenant**.
- Fonctionne avec la version « mail » (boîte réglée). L'avis de remise dépend du fournisseur ; l'accusé de lecture dépend du destinataire.

## 2.11.2 — 31 octobre 2026

**Déclaration de grève (DII) : accusé de réception (PC et Android)**
- Case **Demander un accusé de réception** (cochée par défaut, choix gardé) lors de l'envoi de la DII.
- Envoi direct (version « mail ») : **avis de remise** demandé au serveur d'envoi quand il le permet (DSN) et **accusé de lecture** demandé au destinataire (sa messagerie lui propose de le renvoyer). Les deux arrivent dans votre boîte mail.
- Après l'envoi, la date et l'heure d'envoi sont affichées, avec ce qui a été demandé.
- Envoi par l'application mail ou la messagerie du PC : l'accusé se demande dans les options de cette application.

## 2.11.1 — 31 octobre 2026

**Déclaration de grève (DII) : destinataire, horaires du service, signature (PC et Android)**
- **Destinataire** proposé : l'adresse mail écrite en haut à gauche du formulaire vierge, lue dans le PDF choisi (rien n'est inscrit dans l'application). Modifiable ; PC : bouton « Adresse du formulaire ».
- **Horaires du service prévu** : bouton « Grève sur le service prévu : de PS à FS » (heures de la commande du jour). Partie 1 à la prise de service, partie 2 « reprendre le travail » à la fin de service, le lendemain pour un service de nuit. Heures toujours modifiables à la main ; la reprise propose la fin de service par défaut.
- **Signature** : cadre de dessin plus grand sur le téléphone ; sur le PC, **Dessiner…** à la souris ou au stylet (en plus d'une image). Signature rognée au trait et posée un peu plus grande dans le formulaire.

## 2.11.0 — 30 octobre 2026

**Déclaration de grève (formulaire DII) : remplir et envoyer (PC et Android)**
- Planning : bouton **Déclaration de grève (DII)** dans le détail du jour (téléphone), et clic droit sur un jour › **Déclaration de grève (DII)…** ou Fichier › Déclaration de grève (DII)… (PC).
- Le « Cadre réservé à l'agent » du formulaire se remplit : préavis, **partie 1** (intention de participer à la grève : date, heure, lieu, date) et/ou **partie 2** (suite à la DII n°…, renoncer ou reprendre le travail, date, heure).
- Pré-remplis : nom et prénom d'après le planning, date du jour choisi, heure de prise de service de ce jour, date du jour. CP, entité, lieu, destinataire et message sont gardés pour la fois suivante.
- **Signature** : dessinée au doigt sur le téléphone (gardée si on le souhaite) ; image choisie sur le PC. Elle est posée dans le cadre « Signature » de chaque partie remplie.
- **Envoi** au destinataire indiqué, avec le formulaire rempli en pièce jointe : directement par l'application (version « mail », boîte réglée), sinon par l'application mail (téléphone : PDF déjà joint ; PC : fichier à joindre, dossier ouvert). **Enregistrer le PDF** aussi possible.
- Option : marquer ce jour comme jour de grève dans le planning.
- Le formulaire vierge n'est **pas** fourni avec l'application : on le choisit une fois (PDF reçu de l'établissement), il reste sur l'appareil.

## 2.10.0 — 30 octobre 2026

**Sauvegarde des bulletins sur un NAS ou un kDrive (PC et Android)**
- Tous les bulletins rangés, avec leurs dossiers année et mois, peuvent être copiés sur un autre support. Seuls les nouveaux ou modifiés partent ; rien n'est effacé à la destination.
- **Téléphone** : Réglages › **Sauvegarde des bulletins** — NAS Synology, NAS QNAP, kDrive ou autre serveur WebDAV, en HTTPS.
  - **Sauvegarder maintenant**, **Tout renvoyer**, ou sauvegarde automatique après chaque nouveau bulletin.
  - **Reprendre le serveur du partage du planning** recopie l'adresse, le compte et le certificat déjà réglés.
  - Certificat d'origine du NAS ou adresse IP : « Faire confiance à ce certificat », comme pour le partage.
  - Bouton **Sauvegarder sur le NAS** dans Commandes › Bulletins rangés.
- **PC** : Fichier › **Sauvegarder les bulletins (NAS, kDrive…)…** — copie du dossier « commande » vers un dossier au choix : partage réseau du NAS (\\\\NAS\\dossier ou lecteur réseau), dossier kDrive synchronisé, clé USB… ; à la demande ou automatiquement après chaque nouveau bulletin rangé.

## 2.9.8 — 30 octobre 2026

**Confirmation de réception : bouton d'essai (PC et Android)**
- Réglages › Confirmation de réception (téléphone) et Fichier › Messages de confirmation… (PC) : **Envoyer un essai**. Le message affiché part, rempli avec une commande d'exemple et marqué « [Essai] », par défaut à votre propre adresse.
- Le résultat s'affiche en clair : envoyé (avec le serveur d'envoi et le port utilisés), ou la raison de l'échec (mot de passe refusé, serveur injoignable…).
- Si l'envoi direct n'est pas possible, l'essai dit ce qui manque (adresse, mot de passe, serveur d'envoi SMTP) et ouvre l'application mail avec le message.
- PC : correction de la fenêtre « Confirmer la réception » de la 2.9.7, où la liste Modèle et les boutons se superposaient.

## 2.9.7 — 30 octobre 2026

**Confirmation de réception : un message par expéditeur (PC et Android)**
- Trois messages préenregistrés, un pour chacun des trois expéditeurs des commandes : réglage dans Réglages › Confirmation de réception (téléphone : boutons Message 1, 2, 3) et Fichier › Messages de confirmation… (PC : liste en haut de la fenêtre).
- « Confirmer la réception » prend tout seul le message de l'expéditeur du mail reçu. La liste **Modèle** permet d'en prendre un autre, et le texte reste modifiable avant l'envoi.
- Le message déjà réglé devient le message 1 ; tant qu'ils ne sont pas réglés, les messages 2 et 3 le reprennent.
- Concerne les versions « mail ».

## 2.9.6 — 30 octobre 2026

**Commandes par mail : un troisième expéditeur (PC et Android)**
- Réglages › Commandes par mail (téléphone) et Fichier › Commandes par mail… (PC) : champ **Expéditeur 3**, en plus des deux premiers. Les bulletins de ces trois adresses sont relevés.
- « Trouver les expéditeurs dans la boîte » : on peut maintenant en choisir **trois au plus** (téléphone : toucher les adresses ; PC : les cocher).
- Téléphone : bouton Contacts aussi pour l'expéditeur 3.
- Concerne les versions « mail » (`PlanningCommandes-mail.apk`, `PlanningPDF-mail.exe`) ; les réglages déjà enregistrés sont gardés.

## 2.9.5 — 28 octobre 2026

**Partage du planning : NAS joint par son adresse IP (Android)**
- Avec l'adresse IP du NAS (ou son certificat d'origine), l'envoi échouait : « certificat du serveur non reconnu ». Let's Encrypt ne délivre pas de certificat pour une adresse IP.
- L'application propose maintenant de **faire confiance au certificat du NAS** : elle affiche son empreinte SHA-256 ; une fois accepté, seul CE certificat est accepté pour ce serveur. Un autre certificat à la même adresse est refusé, et la question est reposée.
- Rien n'est envoyé (ni identifiant, ni mot de passe, ni planning) tant que le certificat n'est pas accepté.
- Réglages › Partage du planning : l'empreinte acceptée est affichée, pour comparer avec l'autre téléphone, avec un bouton « Ne plus faire confiance ». Changer l'adresse du serveur oublie le certificat accepté.
- Même fonctionnement sur le téléphone en lecture.
- Programme PC : aucun changement.

## 2.9.4 — 28 octobre 2026

**Partage du planning par un NAS Synology (Android)**
- Le partage avec la version lecture peut passer par votre **NAS Synology** : son accès WebDAV (paquet gratuit « WebDAV Server ») sert à la place du kDrive, dont l'offre gratuite n'a pas de WebDAV.
- Réglages › Partage du planning : choix du serveur **NAS Synology** (proposé d'abord), **kDrive** ou **Autre WebDAV**. Les champs, l'aide et le dossier proposé s'adaptent :
  - NAS : adresse DDNS (ex. `monnas.synology.me`, port 5006 en HTTPS pris par défaut), utilisateur du NAS, mot de passe, dossier partagé `Planning`.
- « Comment faire ? » pour le NAS : installer WebDAV Server (HTTPS), créer le dossier partagé et un utilisateur réservé, DDNS et port 5006 sur la box, certificat Let's Encrypt.
- Messages d'erreur adaptés : NAS injoignable (port non redirigé, NAS éteint), certificat non reconnu, droits de l'utilisateur, WebDAV non autorisé, dossiers partagés visibles.
- Les adresses en `http://` sont refusées : le mot de passe et le planning ne circulent qu'en HTTPS.
- Programme PC : aucun changement.

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
