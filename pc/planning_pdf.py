#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Planning PDF
------------
Lit un ou plusieurs « Bulletins de commande » (PDF sous forme de tableau
Date / Utilisation / Composition) et remplit un planning mensuel.

Fonctionne sous Windows, macOS et Linux.
Dépendances : pdfplumber, Pillow (voir requirements.txt) - Tkinter est fourni avec Python.
"""

import calendar
import datetime as dt
import io
import json
import os
import math
import re
import sys
import tempfile
from collections import Counter

from PIL import Image, ImageDraw, ImageFont

# dossier du programme (à côté de l'exe si version .exe)
if getattr(sys, "frozen", False):
    APP_DIR = os.path.dirname(os.path.abspath(sys.executable))
else:
    APP_DIR = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------------------
#  Règles communes au programme PC et à la version mobile : regles.json
#  (couleurs, codes de repos, thèmes)
# ---------------------------------------------------------------------------
# icône des fenêtres (calendrier + train), PNG 32 et 64 px en base64 (même dessin que icone.ico)
ICONE_32 = (
    "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")
ICONE_64 = (
    "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"
    "YiTMKZxwAAAAAElFTkSuQmCC")

FICHIER_REGLES = "regles.json"


def _chemin_regles():
    """regles.json intégré à l'exe (PyInstaller --add-data), sinon à côté du programme."""
    for base in (getattr(sys, "_MEIPASS", None), os.path.dirname(os.path.abspath(__file__)), APP_DIR):
        if base and os.path.exists(os.path.join(base, FICHIER_REGLES)):
            return os.path.join(base, FICHIER_REGLES)
    raise FileNotFoundError(f"{FICHIER_REGLES} introuvable : il doit se trouver à côté de planning_pdf.py.")


def _tuples(v):
    """Listes JSON -> tuples (couleurs RVB), récursivement dans les dictionnaires."""
    if isinstance(v, list):
        return tuple(v)
    if isinstance(v, dict):
        return {k: _tuples(x) for k, x in v.items()}
    return v


with open(_chemin_regles(), encoding="utf-8") as _f:
    REGLES = json.load(_f)


def dossier_donnees():
    """Dossier où le planning est mémorisé entre deux utilisations."""
    if sys.platform.startswith("win"):
        base = os.environ.get("APPDATA") or os.path.expanduser("~")
    elif sys.platform == "darwin":
        base = os.path.expanduser("~/Library/Application Support")
    else:
        base = os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share")
    d = os.path.join(base, "PlanningPDF")
    os.makedirs(d, exist_ok=True)
    return d


FICHIER_ETAT = "planning.json"
FICHIER_PREFS = "preferences.json"


# ---------------------------------------------------------------------------
#  Mise à jour automatique de PlanningPDF.exe (release « exe » du dépôt GitHub)
#  La recette GitHub intègre version_build.json : {"version": N, "depot": "pseudo/planning-commandes"}
#  et publie version.json ({"version": N}) à côté de PlanningPDF.exe dans la release « exe ».
# ---------------------------------------------------------------------------
FICHIER_VERSION = "version_build.json"


def version_build():
    """(numéro de fabrication GitHub, dépôt) de cet exe, ou (None, "") s'il n'a pas été fabriqué par GitHub."""
    for base in (getattr(sys, "_MEIPASS", None), os.path.dirname(os.path.abspath(__file__))):
        if not base:
            continue
        try:
            with open(os.path.join(base, FICHIER_VERSION), encoding="utf-8") as f:
                d = json.load(f)
            v, depot = d.get("version"), str(d.get("depot") or "").strip()
            if isinstance(v, int) and re.fullmatch(r"[\w.-]+/[\w.-]+", depot):
                return v, depot
        except (OSError, ValueError, AttributeError):
            pass
    return None, ""


def version_appli():
    """Numéro de version du projet (fichier VERSION, ex. « 2.0.0 »), intégré à l'exe ; « dev » s'il manque."""
    for base in (getattr(sys, "_MEIPASS", None), os.path.dirname(os.path.abspath(__file__))):
        if not base:
            continue
        try:
            with open(os.path.join(base, "VERSION"), encoding="utf-8") as f:
                v = f.read().strip()
            if v:
                return v
        except OSError:
            pass
    return "dev"


def _adresse_release(depot, fichier):
    return f"https://github.com/{depot}/releases/download/exe/{fichier}"


def _ouvrir_url(adresse, delai=20):
    """Réponse HTTP (redirections suivies) ; essaie aussi les certificats de « certifi » si présents."""
    import ssl
    import urllib.error
    import urllib.request
    contextes = [ssl.create_default_context()]
    try:
        import certifi
        contextes.append(ssl.create_default_context(cafile=certifi.where()))
    except ImportError:
        pass
    req = urllib.request.Request(adresse, headers={"User-Agent": "PlanningPDF"})
    derniere = None
    for ctx in contextes:
        try:
            return urllib.request.urlopen(req, timeout=delai, context=ctx)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise OSError("aucune version publiée trouvée (dépôt privé ou release « exe » absente)")
            raise OSError(f"réponse {e.code} de GitHub")
        except urllib.error.URLError as e:
            derniere = e
            if not isinstance(getattr(e, "reason", None), ssl.SSLError):
                break
    raison = getattr(derniere, "reason", derniere)
    if isinstance(raison, ssl.SSLError):
        raise OSError("connexion sécurisée impossible")
    if "timed out" in str(raison):
        raise OSError("GitHub ne répond pas (délai dépassé)")
    raise OSError("pas de connexion Internet")


def version_publiee(depot):
    """Numéro de la dernière version de l'exe publiée sur GitHub."""
    with _ouvrir_url(_adresse_release(depot, "version.json")) as r:
        try:
            return int(json.loads(r.read(65536).decode("utf-8")).get("version"))
        except (ValueError, TypeError, AttributeError):
            raise OSError("fichier de version illisible")


def telecharger_exe(depot, destination, progression=None):
    """Télécharge la nouvelle PlanningPDF.exe dans « destination » (vérifie que c'est bien un programme Windows)."""
    with _ouvrir_url(_adresse_release(depot, NOM_EXE), delai=60) as r:          # même variante
        total = int(r.headers.get("Content-Length") or 0)
        lu, dernier = 0, -1
        with open(destination + ".tmp", "wb") as f:
            while True:
                bloc = r.read(1 << 16)
                if not bloc:
                    break
                f.write(bloc)
                lu += len(bloc)
                pct = lu * 100 // total if total else -1
                if progression and pct != dernier and pct % 10 == 0:
                    dernier = pct
                    progression(pct)
    with open(destination + ".tmp", "rb") as f:
        if f.read(2) != b"MZ" or lu < 1_000_000:
            os.remove(destination + ".tmp")
            raise OSError("le fichier téléchargé n'est pas un programme Windows")
    os.replace(destination + ".tmp", destination)


def script_remplacement(exe, nouveau, pids):
    """Petit .bat qui attend la fermeture du programme, remplace l'exe et le relance."""
    attente = "\r\n".join(
        f'tasklist /FI "PID eq {p}" 2>nul | find "{p}" >nul && (ping -n 2 127.0.0.1 >nul & goto attente)' for p in pids)
    return ("@echo off\r\nchcp 65001 >nul\r\n:attente\r\n" + attente + "\r\n"
            "set /a essais=0\r\n:remplacer\r\n"
            f'move /y "{nouveau}" "{exe}" >nul 2>nul && goto relancer\r\n'
            "set /a essais+=1\r\nif %essais% geq 20 goto fin\r\nping -n 2 127.0.0.1 >nul\r\ngoto remplacer\r\n"
            f':relancer\r\nstart "" "{exe}"\r\n:fin\r\n(goto) 2>nul & del "%~f0"\r\n')


def lire_prefs():
    try:
        with open(os.path.join(dossier_donnees(), FICHIER_PREFS), encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def ecrire_pref(cle, valeur):
    try:
        prefs = lire_prefs()
        prefs[cle] = valeur
        chemin = os.path.join(dossier_donnees(), FICHIER_PREFS)
        with open(chemin + ".tmp", "w", encoding="utf-8") as f:
            json.dump(prefs, f, ensure_ascii=False, indent=1)
        os.replace(chemin + ".tmp", chemin)
    except OSError:
        pass
NOM_PIECE_JOINTE = "planning_donnees.json"     # données glissées dans le PDF de sortie

JOURS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
MOIS_COURT = ["Janv.", "Févr.", "Mars", "Avril", "Mai", "Juin",
              "Juil.", "Août", "Sept.", "Oct.", "Nov.", "Déc."]
MOIS_LONG = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet",
             "août", "septembre", "octobre", "novembre", "décembre"]

# Codes affichés en gris (repos, congés…)
CODES_REPOS = tuple(REGLES["codes_repos"])

# ---------------------------------------------------------------------------
#  Codes spéciaux : lus dans le fichier « codes.txt » à côté du programme
# ---------------------------------------------------------------------------
FICHIER_CODES = "codes.txt"

CODES_TXT_DEFAUT = """\
# ==================================================================
#  CODES SPÉCIAUX DU PLANNING
#  Modifiez ce fichier avec le Bloc-notes, enregistrez :
#  le planning se met à jour tout seul (pas besoin de refaire l'exe).
# ==================================================================
#
#  Une ligne par code, 4 colonnes séparées par des ;
#
#    CODE DU BULLETIN ; NOM AFFICHÉ ; COULEUR DE FOND ; gros
#
#  - CODE DU BULLETIN : tel qu'écrit dans la colonne « Utilisation ».
#  - NOM AFFICHÉ      : ce qui apparaît dans le planning
#                       (remettez le même code pour ne pas le renommer).
#                       [date] = afficher l'intitulé écrit sous la date
#                       dans le bulletin (ex. « GRAISSAGE MONTCLAIR »).
#                       On peut y ajouter du texte : [date] Montclair
#  - COULEUR DE FOND  : un nom parmi
#                       bleu, vert, violet, orange, jaune, rose, rouge,
#                       turquoise, gris, beige, marron, blanc,
#                       noir, gris foncé, bleu foncé, vert foncé, rouge foncé
#                       (sur une couleur foncée, le texte s'écrit en blanc)
#                       ou trois nombres R,V,B (ex. 255,240,200)
#                       ou #code hexadécimal (ex. #FFF0C8)
#                       ou rien pour ne pas colorer la case.
#  - gros             : facultatif. Écrivez  gros  pour afficher le nom
#                       en très gros au centre de la case (comme RP).
#                       Sinon le nom s'affiche juste après le chiffre du jour.
#
#  Les lignes qui commencent par # sont ignorées.
#
# CODE     ; NOM     ; COULEUR  ; gros
VAL002     ; S-VAL   ; bleu
VAL001     ; M-VAL   ; vert
ZBELLE     ; DISPO   ; violet
RP         ; RP      ; orange   ; gros
BLR303T    ; [date]  ;
BLRRTM     ; [date]  ;
BLR302T    ; [date] Montclair ;
"""

COULEURS = _tuples(REGLES["couleurs"])   # voir regles.json

# codes dont la couleur de fond est imposée (regles.json, « fond_bloque ») : repos, congés, fêtes…
_FB = REGLES["fond_bloque"]
FOND_BLOQUE = tuple(COULEURS[_FB["couleur"]])
CODES_FOND_BLOQUE = frozenset(c.upper() for c in list(_FB["codes"]) + list(CODES_REPOS))
_MOTIF_FOND_BLOQUE = re.compile(_FB["motif"], re.I) if _FB.get("motif") else None


def fond_bloque(code):
    """Vrai si la couleur de fond de ce code est imposée (non modifiable)."""
    c = (code or "").strip().upper()
    return bool(c) and (c in CODES_FOND_BLOQUE or bool(_MOTIF_FOND_BLOQUE and _MOTIF_FOND_BLOQUE.match(c)))


# code du bulletin (MAJUSCULES) -> {"nom": str, "fond": (r,v,b)|None, "gros": bool}
CODES = {}
_codes_etat = {"mtime": None, "erreurs": []}


def chemin_codes():
    return os.path.join(APP_DIR, FICHIER_CODES)


def _couleur(txt):
    txt = txt.strip().lower()
    if not txt or txt in ("aucune", "non", "-", "none"):
        return None
    if txt in COULEURS:
        return COULEURS[txt]
    if txt.startswith("#") and len(txt) == 7:
        return tuple(int(txt[i:i + 2], 16) for i in (1, 3, 5))
    parts = [x for x in re.split(r"[,\s]+", txt) if x]
    if len(parts) == 3 and all(x.isdigit() and int(x) <= 255 for x in parts):
        return tuple(int(x) for x in parts)
    raise ValueError(f"couleur inconnue « {txt} »")


def lire_codes(texte):
    codes, erreurs = {}, []
    for n, ligne in enumerate(texte.splitlines(), 1):
        ligne = ligne.strip()
        if not ligne or ligne.startswith("#"):
            continue
        cols = [c.strip() for c in ligne.split(";")]
        try:
            if not cols[0]:
                raise ValueError("code manquant")
            nom = cols[1] if len(cols) > 1 and cols[1] else cols[0]
            fond = _couleur(cols[2]) if len(cols) > 2 else None
            gros = len(cols) > 3 and cols[3].lower() in ("gros", "oui", "x", "1")
            if fond_bloque(cols[0]):
                fond = FOND_BLOQUE                       # couleur imposée, quoi qu'en dise codes.txt
            codes[cols[0].upper()] = {"nom": nom, "fond": fond, "gros": gros}
        except Exception as ex:
            erreurs.append(f"ligne {n} : {ex}")
    return codes, erreurs


def codes_a_jour():
    """(Re)lit codes.txt s'il a changé ; le crée s'il n'existe pas."""
    chemin = chemin_codes()
    if not os.path.exists(chemin):
        try:
            with open(chemin, "w", encoding="utf-8-sig") as f:
                f.write(CODES_TXT_DEFAUT)
        except OSError:
            pass
    try:
        mtime = os.path.getmtime(chemin)
    except OSError:
        mtime = "defaut"
    if mtime == _codes_etat["mtime"]:
        return
    try:
        with open(chemin, encoding="utf-8-sig") as f:
            texte = f.read()
    except UnicodeDecodeError:
        with open(chemin, encoding="cp1252") as f:      # enregistré en ANSI par le Bloc-notes
            texte = f.read()
    except OSError:
        texte = CODES_TXT_DEFAUT
    codes, erreurs = lire_codes(texte)
    CODES.clear()
    CODES.update(codes)
    _codes_etat.update(mtime=mtime, erreurs=erreurs)


def palette_couleurs():
    """Palette de l'éditeur de codes : lignes de 12 teintes, de la plus pâle à la plus
    foncée, puis une ligne de gris."""
    import colorsys
    lignes = []
    for lum, sat in ((0.92, 0.75), (0.84, 0.70), (0.72, 0.65), (0.55, 0.70), (0.36, 0.70)):
        ligne = []
        for k in range(12):
            r, v, b = colorsys.hls_to_rgb(k / 12, lum, sat)
            ligne.append((round(r * 255), round(v * 255), round(b * 255)))
        lignes.append(ligne)
    lignes.append([(g, g, g) for g in (255, 238, 222, 205, 185, 165, 140, 115, 90, 70, 50, 25)])
    return lignes


def lire_codes_fichier():
    """Contenu actuel de codes.txt : [(code, nom, fond, gros)] dans l'ordre du fichier."""
    codes_a_jour()
    try:
        with open(chemin_codes(), encoding="utf-8-sig") as f:
            texte = f.read()
    except UnicodeDecodeError:
        with open(chemin_codes(), encoding="cp1252") as f:
            texte = f.read()
    except OSError:
        texte = CODES_TXT_DEFAUT
    codes, _ = lire_codes(texte)
    return [(k, v["nom"], v["fond"], v["gros"]) for k, v in codes.items()]


def nom_couleur(fond):
    """(r, v, b) -> nom de couleur connu, sinon #RRVVBB ; None -> ''."""
    if not fond:
        return ""
    for nom, rvb in COULEURS.items():
        if tuple(rvb) == tuple(fond) and not nom.endswith(" fonce"):   # forme avec accent
            return nom
    return "#%02X%02X%02X" % tuple(fond)


def ecrire_codes_fichier(lignes):
    """Réécrit codes.txt à partir de [(code, nom, fond, gros)] (l'en-tête d'aide est conservé)."""
    entete = "".join(l + "\n" for l in CODES_TXT_DEFAUT.splitlines() if l.startswith("#"))
    corps = []
    for code, nom, fond, gros in lignes:
        if fond_bloque(code):
            fond = FOND_BLOQUE
        cols = [f"{code:<10}", f"{nom:<16}", f"{nom_couleur(fond):<10}"]
        if gros:
            cols.append("gros")
        corps.append(" ; ".join(cols).rstrip())
    chemin = chemin_codes()
    tmp = chemin + ".tmp"
    with open(tmp, "w", encoding="utf-8-sig") as f:
        f.write(entete + "\n".join(corps) + "\n")
    os.replace(tmp, chemin)


def infos_code(code):
    """Réglages d'un code (code du bulletin, ou nom affiché pour les anciennes données)."""
    if not code:
        return None
    c = code.upper()
    if c in CODES:
        return CODES[c]
    v = next((v for v in CODES.values() if v["nom"].upper() == c), None)
    if v is None and fond_bloque(c):                     # absent de codes.txt : couleur imposée quand même
        v = {"nom": code.strip(), "fond": FOND_BLOQUE, "gros": False}
    return v


def code_affiche(code):
    i = infos_code(code)
    return i["nom"] if i else code


def nom_affiche(sv):
    """Nom à afficher pour un service. [date] dans codes.txt est remplacé par
    l'intitulé écrit sous la date dans le bulletin (ex. « GRAISSAGE MONTCLAIR ») ;
    on peut y ajouter du texte, ex. « [date] Montclair » -> « NUCLEAIRE Montclair »."""
    i = infos_code(sv["code"])
    if not i:
        return sv["code"]
    nom = i["nom"]
    if "[date]" in nom.lower():
        remplacement = sv.get("intitule") or sv["code"]
        nom = re.sub(r"\[date\]", lambda m: remplacement, nom, flags=re.I)
        nom = re.sub(r"\s+", " ", nom).strip()
    return nom

R_DATE = re.compile(r"^(\d{2})/(\d{2})/(\d{4})$")
R_HEURE = re.compile(r"^\d{1,2}[:h]\d{2}$")
R_JOUR = re.compile(r"^(lun|mar|mer|jeu|ven|sam|dim)\.?$", re.I)


# ---------------------------------------------------------------------------
#  Données : un « service » = ce qui s'affiche dans une case du planning
#     {"code": "VAL002", "horaires": "13:28 – 22:35",
#      "libelle": "Agent circulation Valmont - STV", "source": "pdf"|"manuel"}
# ---------------------------------------------------------------------------
def service(code="", horaires="", libelle="", source="manuel", intitule=""):
    # intitule : texte écrit sous la date dans le bulletin (ex. « GRAISSAGE MONTCLAIR »)
    return {"code": code, "horaires": horaires, "libelle": libelle, "source": source,
            "intitule": intitule}


def joli(texte):
    """« AGENT CIRCULATION VALMONT - STV » -> « Agent circulation Valmont - STV »."""
    texte = re.sub(r"\s+", " ", texte).strip()
    if texte and texte.isupper():
        petits = {"A", "À", "DU", "DE", "DES", "LA", "LE", "LES", "ET", "AU", "AUX", "EN", "D", "L"}
        mots = texte.split(" ")
        out = []
        for i, m in enumerate(mots):
            if i == 0:
                out.append(m.capitalize())
            elif m in petits:
                out.append(m.lower().replace("a", "à") if m == "A" else m.lower())
            elif len(m) <= 3 and m.isalpha() and m not in petits:
                out.append(m)                    # sigles : STV, PRS…
            else:
                out.append(m.capitalize())
        texte = " ".join(out)
    return texte


# ---------------------------------------------------------------------------
#  Lecture du bulletin de commande
# ---------------------------------------------------------------------------
def _lignes(mots, tol=3):
    """Regroupe des mots (pdfplumber) en lignes selon leur position verticale."""
    lignes = []
    for w in sorted(mots, key=lambda w: (w["top"], w["x0"])):
        if lignes and abs(lignes[-1][0]["top"] - w["top"]) <= tol:
            lignes[-1].append(w)
        else:
            lignes.append([w])
    return [sorted(l, key=lambda w: w["x0"]) for l in lignes]


def lire_bulletin(chemin):
    """
    Renvoie un dict :
      {"agent": str, "edition": datetime|None, "debut": date|None, "fin": date|None,
       "jours": {date: [service, ...]}}
    """
    import pdfplumber

    info = {"agent": "", "edition": None, "debut": None, "fin": None, "jours": {},
            "fichier": os.path.abspath(chemin), "empreinte": ""}
    try:
        info["empreinte"] = _empreinte(chemin)
    except OSError:
        pass
    with pdfplumber.open(chemin) as pdf:
        texte_total = "\n".join((p.extract_text() or "") for p in pdf.pages)
        if not texte_total.strip():
            raise ValueError("Ce PDF ne contient pas de texte lisible (document scanné ?).")

        m = re.search(r"Agent\s*:\s*(.+)", texte_total)
        if m:
            info["agent"] = m.group(1).strip()
        m = re.search(r"Edition le\s*(\d{2}/\d{2}/\d{4})\s*,?\s*(\d{1,2}:\d{2})?", texte_total)
        if m:
            info["edition"] = dt.datetime.strptime(
                m.group(1) + " " + (m.group(2) or "00:00"), "%d/%m/%Y %H:%M")
        m = re.search(r"allant du\s*(\d{2}/\d{2}/\d{4})\s*au\s*(\d{2}/\d{2}/\d{4})", texte_total)
        if m:
            info["debut"] = dt.datetime.strptime(m.group(1), "%d/%m/%Y").date()
            info["fin"] = dt.datetime.strptime(m.group(2), "%d/%m/%Y").date()

        # Lignes brutes de chaque jour : [(date, colonne, [mots])]
        blocs = []           # [date, [lignes utilisation], [lignes composition]]
        col_util = col_comp = None
        entete_p1 = []            # mots du haut de la 1re page (répétés en haut des pages suivantes)

        def meme_mot(a, b):
            return a["text"] == b["text"] and abs(a["x0"] - b["x0"]) < 3 and abs(a["top"] - b["top"]) < 3

        for n_page, page in enumerate(pdf.pages):
            mots = page.extract_words(keep_blank_chars=False)
            # en-tête du tableau (répété ou non sur chaque page)
            entete = next((w for w in mots if w["text"] == "Utilisation"), None)
            haut = entete["bottom"] + 1 if entete else 0
            if entete:
                col_util = entete["x0"] - 10
                if not entete_p1:
                    entete_p1 = [w for w in mots if w["top"] < entete["top"] - 2]
            elif entete_p1:
                # page sans en-tête de tableau : on saute le cartouche répété
                # (« BULLETIN DE COMMANDE », « Agent : … », « N° CP : … »)
                repetes = [w for w in mots if any(meme_mot(w, e) for e in entete_p1)]
                if repetes:
                    haut = max(w["bottom"] for w in repetes) + 1
            if col_util is None:
                continue          # pas de tableau sur cette page

            # fin du tableau : « Fin d'impression », « Signature » ou pied de page
            bas = page.height
            for i, w in enumerate(mots):
                suivant = mots[i + 1]["text"] if i + 1 < len(mots) else ""
                if w["top"] > haut and (
                        w["text"].startswith("Signature")
                        or (w["text"] == "Fin" and suivant.lower().startswith("d'impression"))
                        or (w["text"] == "SOCIETE" and suivant == "NATIONALE")
                        or (w["text"] == "Page" and w["top"] > page.height * 0.85)):
                    bas = min(bas, w["top"] - 1)
            # coordonnées verticales continues d'une page à l'autre (tri correct
            # d'un jour coupé entre deux pages)
            decal = n_page * 100000
            zone = [dict(w, top=w["top"] + decal, bottom=w["bottom"] + decal)
                    for w in mots if haut < w["top"] < bas]
            haut, bas = haut + decal, bas + decal

            # colonne Composition : bord gauche des textes situés nettement
            # à droite du début de la colonne Utilisation
            if entete or col_comp is None:
                cand = [w["x0"] for w in zone if w["x0"] > col_util + 50]
                if cand:
                    col_comp = min(cand) - 3
            if col_comp is None:
                continue

            # débuts de jour : dates (et libellés Lun/Mar…) dans la 1re colonne
            debuts = []
            for w in zone:
                if w["x0"] < col_util and R_DATE.match(w["text"]):
                    j, mo, a = map(int, R_DATE.match(w["text"]).groups())
                    # le libellé du jour (Lun…) est juste au-dessus
                    lab = [x for x in zone if x["x0"] < col_util and R_JOUR.match(x["text"])
                           and 0 <= w["top"] - x["top"] < 18]
                    y = min([x["top"] for x in lab] + [w["top"] - 12])
                    debuts.append((y - 2, dt.date(a, mo, j)))
            debuts.sort()

            # mots situés avant la 1re date de la page -> suite du jour précédent
            limites = [(haut, None)] + debuts + [(bas, "FIN")]
            for (y0, date), (y1, _) in zip(limites, limites[1:]):
                ms = [w for w in zone if y0 <= w["top"] < y1 and w["x0"] >= col_util]
                # intitulé écrit sous la date (ex. « GRAISSAGE MONTCLAIR »)
                md = [w for w in zone if y0 <= w["top"] < y1 and w["x0"] < col_util
                      and not R_DATE.match(w["text"]) and not R_JOUR.match(w["text"])]
                if date is None:
                    if not blocs or not (ms or md):
                        continue
                    bloc = blocs[-1]
                else:
                    bloc = [date, [], [], []]
                    blocs.append(bloc)
                bloc[1] += [w for w in ms if w["x0"] < col_comp]
                bloc[2] += [w for w in ms if w["x0"] >= col_comp]
                bloc[3] += md

    # Interprétation de chaque jour
    for date, m_util, m_comp, m_date in blocs:
        intitule = " ".join(" ".join(w["text"] for w in l) for l in _lignes(m_date))
        l_util = [" ".join(w["text"] for w in l) for l in _lignes(m_util)]
        code = next((l for l in l_util if not l.lower().startswith("du ")), "")
        code = code.split(" ")[0] if code else ""

        # Toutes les lignes horaires, dans l'ordre du bulletin :
        #   « AUTO 12:38 13:28 » -> « AUTO 12:38 – 13:28 », « PS 13:28 », « FS 22:35 », « K 12:00 – 13:00 »…
        libelle, horaires = [], []
        for l in _lignes(m_comp):
            t = [w["text"] for w in l]
            if t[0].lower().startswith("fin"):
                continue
            heures = [x.replace("h", ":") for x in t if R_HEURE.match(x)]
            if heures:
                etiquette = " ".join(x for x in t if not R_HEURE.match(x))
                horaires.append(f"{etiquette} {' – '.join(heures)}".strip())
            else:
                libelle.append(" ".join(t))
        info["jours"].setdefault(date, []).append(
            service(code, " / ".join(horaires), joli(" ".join(libelle)), "pdf", intitule))
    return info


# ---------------------------------------------------------------------------
#  Dossier « commande » : renommage automatique des bulletins
# ---------------------------------------------------------------------------
DOSSIER_COMMANDES = "commande"
R_PERIODE = re.compile(r"allant\s+du\s*(\d{2})/(\d{2})/(\d{4})\s*au\s*(\d{2})/(\d{2})/(\d{4})", re.I)
# Nom classé : « 2026-10-02 au 2026-10-13 - Commande.pdf »
R_DEJA_NOMME = re.compile(r"^(\d{4})-(\d{2})-\d{2} au \d{4}-\d{2}-\d{2} - Commande( \(.*\))?\.pdf$", re.I)
# Bulletins « Contrairement » : « Contrairement Commande allant du 29-09-2026 au 29-09-2026.pdf »
R_DEJA_NOMME_C = re.compile(r"^Contrairement Commande allant du \d{2}-(\d{2})-(\d{4}) au \d{2}-\d{2}-\d{4}( \(.*\))?\.pdf$", re.I)
R_EDITION = re.compile(r"Edition\s+le\s*(\d{2})/(\d{2})/(\d{4})\s*,?\s*(\d{1,2}):(\d{2})", re.I)


def dossier_commandes():
    d = os.path.join(APP_DIR, DOSSIER_COMMANDES)
    os.makedirs(d, exist_ok=True)
    return d


def sous_dossier(annee, mois):
    """« 2026\\10 - Octobre » : rangement par année puis par mois de début."""
    return os.path.join(f"{annee:04d}", f"{mois:02d} - {MOIS_LONG[mois - 1].capitalize()}")


def nom_commande(chemin, contrairement=False):
    """Renvoie (nom, édition, (année, mois)) d'après le bulletin, ou (None, None, None).
    Nom : « 2026-10-02 au 2026-10-13 - Commande » (date inversée : tri chronologique),
    ou, pour un bulletin « Contrairement » :
          « Contrairement Commande allant du 29-09-2026 au 29-09-2026 »."""
    import pdfplumber
    with pdfplumber.open(chemin) as pdf:
        texte = (pdf.pages[0].extract_text() or "") if pdf.pages else ""
    m = R_PERIODE.search(texte)
    if not m:
        return None, None, None
    j1, m1, a1, j2, m2, a2 = m.groups()
    if contrairement:
        nom = f"Contrairement Commande allant du {j1}-{m1}-{a1} au {j2}-{m2}-{a2}"
    else:
        nom = f"{a1}-{m1}-{j1} au {a2}-{m2}-{j2} - Commande"
    e = R_EDITION.search(texte)
    edition = f"édition du {e.group(3)}-{e.group(2)}-{e.group(1)} {int(e.group(4)):02d}h{e.group(5)}" if e else ""
    return nom, edition, (int(a1), int(m1))


_EMPREINTES = {}          # chemin -> ((taille, date de modif.), md5) : évite de relire les PDF


def _empreinte(f):
    """Empreinte md5 du fichier, recalculée seulement s'il a changé."""
    import hashlib
    st = os.stat(f)
    cle = (st.st_size, st.st_mtime_ns)
    deja = _EMPREINTES.get(f)
    if deja and deja[0] == cle:
        return deja[1]
    with open(f, "rb") as fh:
        md5 = hashlib.md5(fh.read()).hexdigest()
    _EMPREINTES[f] = (cle, md5)
    return md5


def _tous_les_pdf(dossier):
    for racine, _, fichiers in os.walk(dossier):
        for f in fichiers:
            if f.lower().endswith(".pdf"):
                yield os.path.join(racine, f)


def ajouter_au_dossier(chemin, dossier=None):
    """Copie un bulletin dans le dossier « commande » (l'original reste en place),
    puis le range et le renomme d'après sa période. Renvoie le nouveau chemin
    relatif, ou None si le fichier y était déjà (même contenu)."""
    import shutil
    dossier = dossier or dossier_commandes()
    abs_d = os.path.normcase(os.path.abspath(dossier))
    if os.path.normcase(os.path.abspath(chemin)).startswith(abs_d + os.sep):
        return None                                    # déjà dans le dossier
    taille, emp = os.path.getsize(chemin), None
    for g in _tous_les_pdf(dossier):
        if os.path.getsize(g) == taille:
            emp = emp or _empreinte(chemin)
            if _empreinte(g) == emp:
                return None                            # déjà copié auparavant
    base, k = os.path.basename(chemin), 1
    dst = os.path.join(dossier, base)
    while os.path.exists(dst):
        k += 1
        dst = os.path.join(dossier, f"{os.path.splitext(base)[0]} ({k}).pdf")
    shutil.copy2(chemin, dst)
    renommes, _, _ = ranger_commandes(dossier)
    rel = os.path.relpath(dst, dossier)
    for ancien, nouveau in renommes:
        if ancien == rel:
            return nouveau
    return None


def ranger_commandes(dossier=None):
    """Range les PDF du dossier « commande » (et de ses sous-dossiers) :
        commande\\2026\\10 - Octobre\\2026-10-02 au 2026-10-13 - Commande.pdf
    Deux bulletins de même période : l'édition est ajoutée au nom.
    Renvoie (rangés [(ancien, nouveau) relatifs], non reconnus [nom], erreurs [texte])."""
    dossier = dossier or dossier_commandes()
    renommes, inconnus, erreurs = [], [], []

    for src in sorted(_tous_les_pdf(dossier)):
        rel = os.path.relpath(src, dossier)
        f = os.path.basename(src)
        contrairement = "contrairement" in f.lower()
        m = R_DEJA_NOMME.match(f)
        mc = R_DEJA_NOMME_C.match(f)
        if m and os.path.normcase(os.path.dirname(rel)) == \
                os.path.normcase(sous_dossier(int(m.group(1)), int(m.group(2)))):
            continue                                   # déjà bien rangé et bien nommé
        if mc and os.path.normcase(os.path.dirname(rel)) == \
                os.path.normcase(sous_dossier(int(mc.group(2)), int(mc.group(1)))):
            continue
        try:
            nom, edition, annee_mois = nom_commande(src, contrairement)
        except Exception as ex:
            erreurs.append(f"{rel} : illisible ({ex})")
            continue
        if not nom:
            if os.path.dirname(rel) == "":
                inconnus.append(rel)
            continue
        cible = os.path.join(dossier, sous_dossier(*annee_mois))
        os.makedirs(cible, exist_ok=True)
        candidats = [nom + ".pdf"]
        if edition:
            candidats.append(f"{nom} ({edition}).pdf")
        candidats += [f"{nom} ({edition or 'copie'} {k}).pdf" for k in range(2, 50)]
        for c in candidats:
            dst = os.path.join(cible, c)
            if os.path.normcase(dst) == os.path.normcase(src):
                break
            if not os.path.exists(dst):
                import time
                for essai in range(4):          # antivirus / aperçu Windows : on réessaie
                    try:
                        os.replace(src, dst)
                        renommes.append((rel, os.path.relpath(dst, dossier)))
                        break
                    except OSError as ex:
                        if essai == 3:
                            erreurs.append(f"{rel} : OUVERT dans un autre programme, pas encore rangé "
                                           f"(fermez-le puis cliquez sur « Dossier commande ») [{ex.__class__.__name__}]")
                        else:
                            time.sleep(0.4)
                break
            if _empreinte(dst) == _empreinte(src):     # doublon exact déjà présent
                erreurs.append(f"{rel} : identique à « {os.path.relpath(dst, dossier)} » (laissé tel quel)")
                break
    return renommes, inconnus, erreurs


# Numéro à augmenter quand la lecture des bulletins est améliorée :
# tous les bulletins du dossier sont alors relus automatiquement.
VERSION_LECTURE = 4


def trouver_bulletin(sv, dossier=None):
    """Chemin actuel du bulletin d'où vient un service (retrouvé par son empreinte
    dans le dossier « commande », sinon à son emplacement d'origine), ou None."""
    emp = sv.get("empreinte", "")
    if emp:
        try:
            for f in _tous_les_pdf(dossier or dossier_commandes()):
                if _empreinte(f) == emp:
                    return f
        except OSError:
            pass
    f = sv.get("fichier", "")
    return f if f and os.path.exists(f) else None


# ---------------------------------------------------------------------------
#  Commandes reçues par mail (IMAP, Free par défaut) → dossier « commande »
#  (même fonctionnement que l'application Android : ReleveMail / CourrierImap)
# ---------------------------------------------------------------------------
def _version_mail():
    """Version de l'exe, inscrite par la recette GitHub dans version_build.json (« mail ») :
    PlanningPDF.exe = version manuelle, SANS mail (comme avant) ; PlanningPDF-mail.exe = avec les commandes par mail.
    Sans ce fichier (planning_pdf.py lancé directement, exe fabriqué sur le PC) : avec les commandes par mail."""
    for base in (getattr(sys, "_MEIPASS", None), os.path.dirname(os.path.abspath(__file__))):
        if not base:
            continue
        try:
            with open(os.path.join(base, FICHIER_VERSION), encoding="utf-8") as f:
                return bool(json.load(f).get("mail", True))
        except (OSError, ValueError, AttributeError):
            pass
    return True


MAIL_DISPONIBLE = _version_mail()          # False : version manuelle (aucune fonction mail : ni menu, ni relevé)
NOM_EXE = "PlanningPDF-mail.exe" if MAIL_DISPONIBLE else "PlanningPDF.exe"   # nom publié sur GitHub (même version)
MAIL_SERVEUR, MAIL_PORT = "imap.free.fr", 993
MAIL_JOURS_DEPART = 30                     # premier relevé : messages des 30 derniers jours
# Conditions pour garder un PDF : expéditeur EXACTEMENT l'un des expéditeurs choisis, et nom du fichier contenant
# l'un de ces mots (sans majuscules, accents, « _ » ni « - »). Mêmes valeurs que CourrierImap.NOMS_PDF (Android).
MAIL_NOMS_PDF = ("bulletin de commande", "contrairement")
MAIL_INTERVALLE_MS = 60 * 60 * 1000        # puis toutes les heures, tant que le programme est ouvert


def proteger(texte):
    """Mot de passe protégé par Windows (DPAPI : lisible seulement par ce compte Windows, sur ce PC)."""
    import base64
    if sys.platform.startswith("win"):
        import ctypes
        from ctypes import wintypes

        class BLOB(ctypes.Structure):
            _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]
        donnees = texte.encode("utf-8")
        entree = BLOB(len(donnees), ctypes.cast(ctypes.create_string_buffer(donnees, len(donnees)), ctypes.POINTER(ctypes.c_char)))
        sortie = BLOB()
        if not ctypes.windll.crypt32.CryptProtectData(ctypes.byref(entree), "PlanningPDF", None, None, None, 0, ctypes.byref(sortie)):
            raise OSError("protection du mot de passe impossible")
        try:
            return "dpapi:" + base64.b64encode(ctypes.string_at(sortie.pbData, sortie.cbData)).decode()
        finally:
            ctypes.windll.kernel32.LocalFree(sortie.pbData)
    return "b64:" + base64.b64encode(texte.encode("utf-8")).decode()      # hors Windows (essais)


def deproteger(protege):
    import base64
    if protege.startswith("dpapi:"):
        import ctypes
        from ctypes import wintypes

        class BLOB(ctypes.Structure):
            _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]
        donnees = base64.b64decode(protege[6:])
        entree = BLOB(len(donnees), ctypes.cast(ctypes.create_string_buffer(donnees, len(donnees)), ctypes.POINTER(ctypes.c_char)))
        sortie = BLOB()
        if not ctypes.windll.crypt32.CryptUnprotectData(ctypes.byref(entree), None, None, None, None, 0, ctypes.byref(sortie)):
            raise OSError("mot de passe à ressaisir (Commandes par mail)")
        try:
            return ctypes.string_at(sortie.pbData, sortie.cbData).decode("utf-8")
        finally:
            ctypes.windll.kernel32.LocalFree(sortie.pbData)
    if protege.startswith("b64:"):
        return base64.b64decode(protege[4:]).decode("utf-8")
    raise OSError("mot de passe à ressaisir (Commandes par mail)")


def _quoter_imap(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _normaliser_nom(s):
    import unicodedata
    s = "".join(c for c in unicodedata.normalize("NFD", s) if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", re.sub(r"[_\-.]+", " ", s.lower())).strip()


def nom_pdf_accepte(nom):
    """Le nom de la pièce jointe contient « bulletin de commande » ou « contrairement »."""
    n = _normaliser_nom(nom)
    return any(mot in n for mot in MAIL_NOMS_PDF)


def expediteur_accepte(de, expediteurs):
    """L'adresse de l'en-tête From est exactement l'un des expéditeurs choisis."""
    from email.utils import parseaddr
    adresse = parseaddr(de or "")[1].strip().lower()
    return any(adresse == e.strip().lower() for e in expediteurs)


def pdf_du_message(brut, expediteurs=None):
    """Bulletins joints à un message (y compris dans une commande transférée) : [(nom, octets)].
    Gardés seulement si l'expéditeur est l'un de « expediteurs » (si donné) et si le nom du PDF le permet."""
    import email
    from email import policy
    msg = email.message_from_bytes(brut, policy=policy.default)
    pieces = []
    if expediteurs is not None and not expediteur_accepte(str(msg.get("From", "")), expediteurs):
        return pieces
    for partie in msg.walk():
        if partie.is_multipart():
            continue
        nom = partie.get_filename() or ""
        type_ = partie.get_content_type()
        if type_ != "application/pdf" and not (nom.lower().endswith(".pdf") and type_.startswith("application/")):
            continue
        donnees = partie.get_payload(decode=True) or b""
        if donnees[:4] == b"%PDF" and nom_pdf_accepte(nom):
            pieces.append((re.sub(r'[\\/:*?"<>|\r\n]', "_", nom).strip() or "commande.pdf", donnees))
    return pieces


def relever_mail(adresse, mot_de_passe, expediteurs, uid_validite=0, dernier_uid=0,
                 serveur=MAIL_SERVEUR, port=MAIL_PORT, ssl=True):
    """Lit la boîte (sans rien modifier : lecture seule, BODY.PEEK) ; renvoie
    ([(uid, nom, octets)], uid_validite, dernier_uid). Lève OSError avec un message clair."""
    import imaplib
    if not expediteurs:
        raise OSError("aucun expéditeur indiqué")
    try:
        imap = imaplib.IMAP4_SSL(serveur, port, timeout=30) if ssl else imaplib.IMAP4(serveur, port, timeout=30)
    except (OSError, imaplib.IMAP4.error) as ex:
        raise OSError(f"serveur {serveur} injoignable ({ex})")
    try:
        try:
            imap.login(adresse, mot_de_passe)
        except imaplib.IMAP4.error:
            raise OSError("connexion refusée : adresse ou mot de passe incorrect")
        typ, _ = imap.select("INBOX", readonly=True)
        if typ != "OK":
            raise OSError("boîte de réception introuvable")
        validite = int((imap.response("UIDVALIDITY")[1] or [b"0"])[0] or 0)
        neuf = not uid_validite or validite != uid_validite
        dernier = 0 if neuf else dernier_uid
        critere = "OR " * (len(expediteurs) - 1) + " ".join("FROM " + _quoter_imap(e) for e in expediteurs)
        if neuf:
            depuis = (dt.date.today() - dt.timedelta(days=MAIL_JOURS_DEPART))
            depuis = f"{depuis.day}-{['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][depuis.month - 1]}-{depuis.year}"
            typ, data = imap.uid("SEARCH", None, f"SINCE {depuis} {critere}")
        else:
            typ, data = imap.uid("SEARCH", None, f"UID {dernier + 1}:* {critere}")
        if typ != "OK":
            raise OSError("recherche refusée par le serveur")
        uids = [int(x) for x in (data[0] or b"").split()]
        pieces, plus_grand = [], dernier
        for uid in uids:
            if not neuf and uid <= dernier_uid:
                continue                                  # « n:* » renvoie toujours le dernier message
            plus_grand = max(plus_grand, uid)
            typ, data = imap.uid("FETCH", str(uid), "(BODY.PEEK[])")
            brut = next((x[1] for x in data if isinstance(x, tuple)), None) if typ == "OK" else None
            if brut:
                pieces += [(uid, nom, octets) for nom, octets in pdf_du_message(brut, expediteurs)]
        return pieces, validite, plus_grand
    finally:
        try:
            imap.logout()
        except Exception:
            pass


def ranger_pieces_mail(pieces, dossier=None):
    """Dépose les PDF reçus dans le dossier « commande » (sauf s'il y est déjà) ; renvoie les chemins créés."""
    import hashlib
    dossier = dossier or dossier_commandes()
    connus = set()
    for f in _tous_les_pdf(dossier):
        try:
            with open(f, "rb") as fh:
                connus.add(hashlib.md5(fh.read()).hexdigest())
        except OSError:
            pass
    crees = []
    for _uid, nom, octets in pieces:
        h = hashlib.md5(octets).hexdigest()
        if h in connus:
            continue
        connus.add(h)
        base, ext = os.path.splitext(nom if nom.lower().endswith(".pdf") else nom + ".pdf")
        chemin, k = os.path.join(dossier, base + ext), 2
        while os.path.exists(chemin):
            chemin, k = os.path.join(dossier, f"{base} ({k}){ext}"), k + 1
        with open(chemin, "wb") as fh:
            fh.write(octets)
        crees.append(chemin)
    return crees


def synchroniser_dossier(planning, dossier=None, changements=None):
    """Ajoute au planning les bulletins du dossier « commande » pas encore lus.
    Renvoie (nb bulletins lus, jours mis à jour, jours ignorés, erreurs)."""
    dossier = dossier or dossier_commandes()
    if getattr(planning, "version_lecture", 0) != VERSION_LECTURE:
        planning.importes = {}                 # lecture améliorée : on relit tout
        planning.version_lecture = VERSION_LECTURE
    nouveaux, erreurs = [], []
    for f in sorted(_tous_les_pdf(dossier)):
        try:
            emp = _empreinte(f)
        except OSError:
            continue
        if emp in planning.importes:
            continue
        rel = os.path.relpath(f, dossier)
        try:
            info = lire_bulletin(f)
        except Exception as ex:
            planning.importes[emp] = "ignoré : " + rel
            if R_DEJA_NOMME.match(os.path.basename(f)) or R_DEJA_NOMME_C.match(os.path.basename(f)):
                erreurs.append(f"{rel} : {ex}")
            continue
        planning.importes[emp] = rel
        if info["jours"]:
            nouveaux.append(info)
    nouveaux.sort(key=lambda i: i["edition"] or dt.datetime.min)
    maj = ign = 0
    for info in nouveaux:
        a, b = planning.integrer(info, changements)
        maj, ign = maj + a, ign + b
    return len(nouveaux), maj, ign, erreurs


# ---------------------------------------------------------------------------
#  Polices (recherche multi-OS)
# ---------------------------------------------------------------------------
_CACHE = {}


def _dossiers_polices():
    d = [os.path.join(APP_DIR, "polices")]
    if sys.platform.startswith("win"):
        d.append(os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts"))
        d.append(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Microsoft", "Windows", "Fonts"))
    elif sys.platform == "darwin":
        d += ["/System/Library/Fonts", "/System/Library/Fonts/Supplemental",
              "/Library/Fonts", os.path.expanduser("~/Library/Fonts")]
    else:
        d += ["/usr/share/fonts", "/usr/local/share/fonts",
              os.path.expanduser("~/.fonts"), os.path.expanduser("~/.local/share/fonts")]
    return [x for x in d if x and os.path.isdir(x)]


def _index_polices():
    if "_index" not in _CACHE:
        idx = {}
        for dossier in _dossiers_polices():
            for racine, _, fichiers in os.walk(dossier):
                for f in fichiers:
                    if f.lower().endswith((".ttf", ".otf", ".ttc")):
                        idx.setdefault(f.lower(), os.path.join(racine, f))
        _CACHE["_index"] = idx
    return _CACHE["_index"]


# Par ordre de préférence. Pour imposer une police, déposez titre.ttf,
# texte.ttf, sans.ttf et/ou sans_gras.ttf dans le dossier « polices ».
_POLICES = {
    "titre": ["titre.ttf", "titre.otf", "cormorantgaramond-regular.ttf", "playfairdisplay-regular.ttf",
              "didot.ttc", "georgia.ttf", "times new roman.ttf", "times.ttc",
              "liberationserif-regular.ttf", "dejavuserif.ttf", "notoserif-regular.ttf"],
    "texte": ["texte.ttf", "texte.otf", "cormorantgaramond-regular.ttf", "georgia.ttf",
              "times new roman.ttf", "times.ttc", "liberationserif-regular.ttf",
              "dejavuserif.ttf", "notoserif-regular.ttf"],
    "sans": ["sans.ttf", "segoeui.ttf", "arial.ttf", "helvetica.ttc", "liberationsans-regular.ttf",
             "dejavusans.ttf", "notosans-regular.ttf"],
    "sans_gras": ["sans_gras.ttf", "segoeuib.ttf", "arialbd.ttf", "arial bold.ttf",
                  "liberationsans-bold.ttf", "dejavusans-bold.ttf", "notosans-bold.ttf"],
}


def police(role, taille):
    taille = max(6, int(taille))
    cle = (role, taille)
    if cle not in _CACHE:
        idx, f = _index_polices(), None
        for n in _POLICES[role]:
            if n in idx:
                try:
                    f = ImageFont.truetype(idx[n], taille)
                    break
                except OSError:
                    pass
        if f is None:
            try:
                f = ImageFont.load_default(size=taille)
            except TypeError:
                f = ImageFont.load_default()
        _CACHE[cle] = f
    return _CACHE[cle]


# ---------------------------------------------------------------------------
#  Rendu du planning
# ---------------------------------------------------------------------------
def _couper(draw, texte, fnt, largeur):
    lignes, cour = [], ""
    for m in texte.split():
        essai = (cour + " " + m).strip()
        if draw.textlength(essai, font=fnt) <= largeur:
            cour = essai
            continue
        if cour:
            lignes.append(cour)
        while draw.textlength(m, font=fnt) > largeur and len(m) > 1:
            k = len(m)
            while k > 1 and draw.textlength(m[:k] + "-", font=fnt) > largeur:
                k -= 1
            lignes.append(m[:k] + "-")
            m = m[k:]
        cour = m
    if cour:
        lignes.append(cour)
    return lignes


# ---------------------------------------------------------------------------
#  Nuits : « DN » le lendemain de la dernière nuit
# ---------------------------------------------------------------------------
CODE_DN = "DN"
_R_PS = re.compile(r"\bPS\s+(\d{1,2}):(\d{2})")
_R_FS = re.compile(r"\bFS\s+(\d{1,2}):(\d{2})")
_R_PLAGE = re.compile(r"(\d{1,2}):(\d{2})\s*[–-]\s*(\d{1,2}):(\d{2})")


def est_nuit(sv):
    """Service de nuit = la fin de service (FS) tombe après minuit (FS < PS)."""
    h = sv.get("horaires", "")
    ps, fs = _R_PS.search(h), _R_FS.search(h)
    if ps and fs:
        debut = int(ps.group(1)) * 60 + int(ps.group(2))
        fin = int(fs.group(1)) * 60 + int(fs.group(2))
    else:                                   # ancien format « 22:25 – 04:45 »
        m = _R_PLAGE.search(h)
        if not m or "PS" in h or "FS" in h:
            return False
        debut = int(m.group(1)) * 60 + int(m.group(2))
        fin = int(m.group(3)) * 60 + int(m.group(4))
    return fin < debut


def pour_affichage(jours):
    """Jours tels qu'affichés dans le planning : DN ajoutés, et « NU » masqué dès
    qu'un autre code figure le même jour (le NU reste visible dans la liste de
    droite et dans le détail du jour)."""
    res = avec_dn(jours)
    for d, v in list(res.items()):
        autres = [sv for sv in v if sv["code"] and sv["code"].upper() != "NU" and sv["source"] != "auto"]
        if autres and any(sv["code"].upper() == "NU" for sv in v):
            res[d] = [sv for sv in v if sv["code"].upper() != "NU"]
    return res


def avec_dn(jours):
    """Copie des jours où « DN » est ajouté le lendemain de chaque dernière nuit."""
    nuits = {d for d, v in jours.items() if any(est_nuit(sv) for sv in v)}
    res = dict(jours)
    for d in nuits:
        lendemain = d + dt.timedelta(days=1)
        if lendemain in nuits:
            continue                        # la série de nuits continue
        existants = res.get(lendemain, [])
        if not any(sv["code"] == CODE_DN for sv in existants):
            res[lendemain] = list(existants) + [service(CODE_DN, source="auto")]
    return res


def filigrane(img, boite, texte="GRÈVE", couleur=(200, 0, 0), opacite=90):
    """Écrit un filigrane en diagonale, semi-transparent, dans la boîte (x0, y0, x1, y1)."""
    x0, y0, x1, y1 = (int(v) for v in boite)
    w, h = x1 - x0, y1 - y0
    if w < 10 or h < 10:
        return
    import math
    angle = math.degrees(math.atan2(h, w))
    diag = math.hypot(w, h)
    taille = h * 0.5
    while taille > 6:                        # le texte occupe ~80 % de la diagonale
        f = police("sans_gras", taille)
        if ImageDraw.Draw(img).textlength(texte, font=f) <= diag * 0.8:
            break
        taille *= 0.94
    f = police("sans_gras", taille)
    bb = f.getbbox(texte)
    calque = Image.new("RGBA", (bb[2] - bb[0] + 20, bb[3] - bb[1] + 20), (0, 0, 0, 0))
    ImageDraw.Draw(calque).text((10 - bb[0], 10 - bb[1]), texte, font=f, fill=couleur + (opacite,))
    calque = calque.rotate(angle, expand=True, resample=Image.BICUBIC)
    px, py = x0 + (w - calque.width) // 2, y0 + (h - calque.height) // 2
    img.paste(calque, (px, py), calque)


def lignes_horaires(horaires):
    """Lignes horaires d'une case ; PS et FS réunis sur la même ligne :
    [« AUTO 12:38 – 13:28 », « PS 13:28  FS 22:35 », « AUTO 22:35 – 23:25 »]."""
    plages = [h.strip() for h in horaires.split("/") if h.strip()]
    res, ps_attente = [], None
    for h in plages:
        if h.startswith("PS "):
            res.append(h)
            ps_attente = len(res) - 1
        elif h.startswith("FS ") and ps_attente is not None:
            res[ps_attente] += "     " + h         # FS rejoint la ligne du PS (espace élargi)
            ps_attente = None
        else:
            res.append(h)
    return res


# ---------------------------------------------------------------------------
#  Thèmes du planning (écran et PDF)
# ---------------------------------------------------------------------------
_THEME_BASE = _tuples(REGLES["themes"]["base"])            # voir regles.json
THEMES_PLANNING = _tuples(REGLES["themes"]["liste"])
THEME_DEFAUT = "Classique"
_theme_courant = {"ecran": THEME_DEFAUT, "pdf": THEME_DEFAUT}


def palette_theme(nom=None):
    return {**_THEME_BASE, **THEMES_PLANNING.get(nom or THEME_DEFAUT, {})}


def regler_themes(ecran, pdf):
    """Thème utilisé à l'écran et thème utilisé pour le PDF / l'impression / les images."""
    _theme_courant["ecran"] = ecran if ecran in THEMES_PLANNING else THEME_DEFAUT
    _theme_courant["pdf"] = pdf if pdf in THEMES_PLANNING else THEME_DEFAUT


def theme_pdf():
    return _theme_courant["pdf"]


def charger_themes():
    """Applique les thèmes mémorisés dans les préférences."""
    prefs = lire_prefs()
    t = prefs.get("theme_planning", THEME_DEFAUT)
    regler_themes(t, t if prefs.get("theme_planning_pdf", True) else THEME_DEFAUT)


def _luminance(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def dessiner_planning(annee, mois, jours, largeur=1600, hauteur=1130, agent="", greves=(), theme=None):
    """Dessine le planning. Renvoie (image PIL, {date: (x0, y0, x1, y1)}).
    greves : dates à marquer du filigrane « GRÈVE ». theme : nom dans THEMES_PLANNING
    (par défaut le thème réglé pour le PDF)."""
    codes_a_jour()
    jours = pour_affichage(jours)
    P = palette_theme(theme or _theme_courant["pdf"])
    W, H = int(largeur), int(hauteur)
    img = Image.new("RGB", (W, H), P["fond"])
    d = ImageDraw.Draw(img)

    GRIS_TITRE, TRAIT, GRILLE = P["titre"], P["trait"], P["grille"]
    WEEKEND, WEEKEND_ENTETE = P["weekend"], P["weekend_entete"]
    CHIFFRE, CHIFFRE_WE = P["chiffre"], P["chiffre_we"]
    fond_sombre = _luminance(P["fond"]) < 140

    u = min(W, H * 1.6)
    x0, x1 = int(u * 0.03), W - int(u * 0.03)
    y_trait = int(u * 0.078)                 # trait sous le titre (titre réduit, en haut)
    h_entete = int(u * 0.028)
    y_grille, y_fin = y_trait + h_entete, H - int(u * 0.022)
    col = (x1 - x0) / 7
    semaines = calendar.Calendar(0).monthdatescalendar(annee, mois)
    NB_LIGNES = 6                      # toujours 6 lignes : cases de même taille tous les mois
    lig = (y_fin - y_grille) / NB_LIGNES
    fin_trait = max(1, int(u * 0.0008))
    ep = max(1, int(u * 0.0015))

    if P["entete"]:                             # bandeau des jours de la semaine
        d.rectangle([x0, y_trait, x1, y_grille], fill=P["entete"])
    for c in (5, 6):
        d.rectangle([x0 + c * col, y_trait, x0 + (c + 1) * col, y_grille], fill=WEEKEND_ENTETE)
        d.rectangle([x0 + c * col, y_grille, x0 + (c + 1) * col, y_fin], fill=WEEKEND)
    if P["hors_mois"]:                          # cases des mois voisins
        for r, sem in enumerate(semaines):
            for c, jour in enumerate(sem):
                if jour.month != mois:
                    d.rectangle([x0 + c * col, y_grille + r * lig, x0 + (c + 1) * col,
                                 y_grille + (r + 1) * lig], fill=P["hors_mois"])
    # cases colorées selon le code (ex. S-VAL en bleu pastel)
    for r, sem in enumerate(semaines):
        for c, jour in enumerate(sem):
            if jour.month != mois:
                continue
            fond = next((infos_code(sv["code"])["fond"] for sv in jours.get(jour, [])
                         if infos_code(sv["code"]) and infos_code(sv["code"])["fond"]), None)
            if not fond and c < 5 and nom_ferie(jour):
                fond = WEEKEND                         # jour férié : teinte des week-ends
            if fond:
                d.rectangle([x0 + c * col, y_grille + r * lig, x0 + (c + 1) * col, y_grille + (r + 1) * lig],
                            fill=fond)
    for r in range(1, NB_LIGNES):
        y = y_grille + r * lig
        d.line([(x0, y), (x1, y)], fill=GRILLE, width=fin_trait)
    for c in range(1, 7):
        x = x0 + c * col
        d.line([(x, y_grille), (x, y_fin)], fill=GRILLE, width=fin_trait)
    for y in (y_trait, y_grille, y_fin):
        d.line([(x0, y), (x1, y)], fill=TRAIT, width=ep)

    f_jour = police("texte", u * 0.0135)
    for c, nom in enumerate(JOURS):
        d.text((x0 + c * col + col / 2, y_trait + h_entete / 2), nom, font=f_jour,
               fill=P["entete_txt_we"] if c >= 5 else P["entete_txt"], anchor="mm")

    # titre : mois en entier suivi de l'année (ex. « Septembre 2026 »), en haut à gauche
    f_titre = police("titre", u * 0.06)
    y_titre = y_trait - u * 0.011
    nom_mois = MOIS_LONG[mois - 1].capitalize()
    d.text((x0, y_titre), nom_mois, font=f_titre, fill=GRIS_TITRE, anchor="ls")
    d.text((x0 + d.textlength(nom_mois + " ", font=f_titre), y_titre), str(annee),
           font=f_titre, fill=P["annee"], anchor="ls")
    if agent:                      # nom de l'agent, à droite
        d.text((x1, y_titre), agent, font=police("texte", u * 0.015), fill=P["txt_doux"], anchor="rs")

    f_num = police("texte", u * 0.02)
    f_code = police("sans_gras", u * 0.0165)     # code spécial à côté du chiffre
    asc_num = f_num.getmetrics()[0]
    pad = u * 0.007
    cases = {}
    for r, sem in enumerate(semaines):
        for c, jour in enumerate(sem):
            if jour.month != mois:
                continue
            cx0, cy0 = x0 + c * col, y_grille + r * lig
            cx1, cy1 = cx0 + col, cy0 + lig
            cases[jour] = (cx0, cy0, cx1, cy1)
            # fond foncé (ex. « noir » dans codes.txt) -> texte en blanc
            fond_j = next((infos_code(sv["code"])["fond"] for sv in jours.get(jour, [])
                           if infos_code(sv["code"]) and infos_code(sv["code"])["fond"]), None)
            # couleurs du texte selon le fond réel de la case (thème ou couleur du code)
            ferie = nom_ferie(jour)
            we = c >= 5 or bool(ferie)
            fond_case = fond_j or (WEEKEND if we else P["fond"])
            sombre = _luminance(fond_case) < 140
            if sombre == fond_sombre and not fond_j:          # couleurs du thème
                TXT, TXT_DOUX, TXT_REPOS = P["txt"], P["txt_doux"], P["txt_repos"]
                c_gros, c_num = P["gros"], (CHIFFRE_WE if we else CHIFFRE)
            elif sombre:                                       # case foncée : texte clair
                TXT, TXT_DOUX, TXT_REPOS = (255, 255, 255), (225, 225, 225), (210, 210, 210)
                c_gros = c_num = (255, 255, 255)
            else:                                              # case claire (ex. pastel du code)
                clair = palette_theme("Classique") if fond_sombre else P
                TXT, TXT_DOUX, TXT_REPOS = clair["txt"], clair["txt_doux"], clair["txt_repos"]
                c_gros = clair["gros"]
                c_num = clair["chiffre_we"] if we else clair["chiffre"]
            base = cy0 + pad + asc_num               # ligne de base du chiffre
            d.text((cx0 + pad, base), str(jour.day), font=f_num, fill=c_num, anchor="ls")
            larg_ferie = 0
            if ferie:                                # nom du jour férié, en haut à droite
                f_fer = police("texte", u * 0.0095)
                d.text((cx0 + col - pad, base), ferie, font=f_fer, fill=TXT_DOUX if fond_j else c_num, anchor="rs")
                larg_ferie = d.textlength(ferie, font=f_fer) + u * 0.006

            # code spécial (S-VAL, M-VAL…) juste après le chiffre de la date
            # le 1er code du jour s'affiche à côté du chiffre (qu'il soit dans codes.txt ou non),
            # sauf les codes « gros » (RP…) écrits au centre de la case
            special = next((sv for sv in jours.get(jour, [])
                            if sv["code"] and not (infos_code(sv["code"]) or {}).get("gros")), None)
            if special:
                xc = cx0 + pad + d.textlength(str(jour.day), font=f_num) + u * 0.009
                nom, larg_nom = nom_affiche(special), cx0 + col - pad - xc - larg_ferie
                # 1 ligne si le nom tient assez gros, sinon 2 lignes (ex. « GRAISSAGE / MONTCLAIR »)
                # intitulé repris de la case Date ([date]) : police plus petite
                par_date = "[date]" in (infos_code(special["code"]) or {}).get("nom", "").lower()
                t_max = u * (0.0105 if par_date else 0.0165)
                t_min_1l = u * (0.0085 if par_date else 0.0125)
                t = t_max
                while d.textlength(nom, font=police("sans_gras", t)) > larg_nom and t > t_min_1l:
                    t *= 0.95
                lignes_nom = [nom]
                if d.textlength(nom, font=police("sans_gras", t)) > larg_nom and " " in nom:
                    mots = nom.split()
                    coupes = [(" ".join(mots[:k]), " ".join(mots[k:])) for k in range(1, len(mots))]
                    lignes_nom = min(coupes, key=lambda c: max(len(c[0]), len(c[1])))
                    t = u * (0.0105 if par_date else 0.0145)
                while max(d.textlength(l, font=police("sans_gras", t)) for l in lignes_nom) > larg_nom \
                        and t > u * 0.007:
                    t *= 0.93
                fc = police("sans_gras", t)
                h_nom = fc.getbbox("Hg")[3] + t * 0.15
                for k, l in enumerate(lignes_nom):
                    d.text((xc, base + k * h_nom), l, font=fc, fill=TXT, anchor="ls")
                decalage = (len(lignes_nom) - 1) * h_nom
            else:
                decalage = 0

            services = jours.get(jour, [])
            if not services:
                continue
            larg = col - 2 * pad
            y0 = cy0 + pad + asc_num + u * 0.010 + decalage      # juste sous le chiffre
            gros = next((sv for sv in services
                         if infos_code(sv["code"]) and infos_code(sv["code"])["gros"]), None)
            haut_dispo = cy1 - pad - y0

            # Contenu de la case : les horaires (le libellé n'est pas affiché),
            # le code s'il n'est pas déjà à côté du chiffre, et les notes ajoutées à la main.
            def composer(taille):
                fg, fn = police("sans_gras", taille), police("sans", taille * 0.9)
                fh = police("sans_gras", taille)
                lignes = []                          # (texte, police, couleur)
                for sv in services:
                    if sv is gros:
                        continue
                    repos = code_affiche(sv["code"]).upper() in CODES_REPOS
                    coul = TXT_REPOS if repos else TXT
                    plages = lignes_horaires(sv["horaires"])
                    code = "" if sv is special else nom_affiche(sv)
                    if code:
                        lignes += [(l, fg, coul) for l in _couper(d, code, fg, larg)]
                    lignes += [(h, fh, coul) for h in plages]   # jamais coupés
                    if sv["source"] == "manuel" and sv["libelle"]:
                        lignes += [(l, fn, TXT_DOUX) for l in _couper(d, sv["libelle"], fn, larg)]
                bb = fg.getbbox("Hg")
                return lignes, (bb[3] - bb[1]) + taille * 0.32      # interligne

            # on réduit la taille jusqu'à ce que tout tienne dans la case
            taille = u * 0.0145
            while True:
                lignes, h_l = composer(taille)
                tient = (len(lignes) * h_l <= haut_dispo and
                         all(d.textlength(t, font=f) <= larg for t, f, _ in lignes))
                if tient or taille <= u * 0.006:
                    break
                taille *= 0.96                                     # réglage fin
            y = y0
            for t, f, coul in lignes:
                if y + h_l > cy1 - pad * 0.3:     # ne jamais déborder sur la case voisine
                    break
                d.text((cx0 + pad, y - f.getbbox("Hg")[1]), t, font=f, fill=coul, anchor="lt")
                y += h_l

            # code en très gros, centré dans la place restante (ex. « RP »)
            if gros:
                texte = nom_affiche(gros)
                if gros["horaires"]:
                    texte += "\n" + gros["horaires"].replace(" / ", "\n")
                h_reste = cy1 - pad - y
                taille = u * 0.05
                while True:
                    f = police("sans_gras", taille)
                    lg = []
                    for par in texte.split("\n"):
                        lg += _couper(d, par, f, larg)
                    h_lg = f.getbbox("Hg")[3] + taille * 0.2
                    if (len(lg) * h_lg <= h_reste and all(d.textlength(l, font=f) <= larg for l in lg)) \
                            or taille <= u * 0.008:
                        break
                    taille *= 0.92
                yy = y + (h_reste - len(lg) * h_lg) / 2
                for l in lg:
                    d.text((cx0 + col / 2, yy), l, font=f, fill=c_gros, anchor="mt")
                    yy += h_lg

    # filigrane « GRÈVE » par-dessus les cases concernées
    for jour in greves or ():
        if jour in cases:
            x_0, y_0, x_1, y_1 = cases[jour]
            filigrane(img, (x_0 + 2, y_0 + 2, x_1 - 2, y_1 - 2))
    return img, cases


A4 = (3508, 2480)        # A4 paysage, 300 dpi


def exporter_image(chemin, annee, mois, jours, agent="", greves=()):
    """Un seul mois en PNG / JPG."""
    img, _ = dessiner_planning(annee, mois, jours, *A4, agent, greves)
    if os.path.splitext(chemin)[1].lower() in (".jpg", ".jpeg"):
        img.save(chemin, "JPEG", quality=95, dpi=(300, 300))
    else:
        img.save(chemin, "PNG", dpi=(300, 300))


def mois_du_planning(planning, en_plus=None):
    ms = {(d.year, d.month) for d in planning.jours}
    if en_plus:
        ms.add(en_plus)
    return sorted(ms)


# ---------------------------------------------------------------------------
#  Jours fériés (France métropolitaine)
# ---------------------------------------------------------------------------
def paques(annee):
    """Dimanche de Pâques (algorithme de Meeus / Butcher)."""
    a, b, c = annee % 19, annee // 100, annee % 100
    d, e = b // 4, b % 4
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    mois = (h + l - 7 * m + 114) // 31
    jour = (h + l - 7 * m + 114) % 31 + 1
    return dt.date(annee, mois, jour)


_FERIES = {}


def jours_feries(annee):
    """{date: nom} des jours fériés de l'année."""
    if annee not in _FERIES:
        p = paques(annee)
        _FERIES[annee] = {
            dt.date(annee, 1, 1): "Jour de l'an", p + dt.timedelta(days=1): "Lundi de Pâques",
            dt.date(annee, 5, 1): "Fête du Travail", dt.date(annee, 5, 8): "Victoire 1945",
            p + dt.timedelta(days=39): "Ascension", p + dt.timedelta(days=50): "Lundi de Pentecôte",
            dt.date(annee, 7, 14): "Fête nationale", dt.date(annee, 8, 15): "Assomption",
            dt.date(annee, 11, 1): "Toussaint", dt.date(annee, 11, 11): "Armistice 1918",
            dt.date(annee, 12, 25): "Noël"}
    return _FERIES[annee]


def nom_ferie(d):
    return jours_feries(d.year).get(d)


# ---------------------------------------------------------------------------
#  Récapitulatif du mois : heures de service, nuits, dimanches et fériés travaillés, repos
# ---------------------------------------------------------------------------
def duree_service(sv):
    """Durée PS -> FS en minutes (nuit : FS le lendemain), None sans PS/FS."""
    h = sv.get("horaires", "")
    ps, fs = _R_PS.search(h), _R_FS.search(h)
    if not (ps and fs):
        return None
    debut = int(ps.group(1)) * 60 + int(ps.group(2))
    fin = int(fs.group(1)) * 60 + int(fs.group(2))
    return (fin - debut) % 1440


def recap_mois(jours, annee, mois, greves=()):
    """{travailles, minutes, nuits, dimanches, feries, repos} pour un mois
    (les jours de grève ne comptent pas comme travaillés)."""
    r = {"travailles": 0, "minutes": 0, "nuits": 0, "dimanches": 0, "feries": 0, "repos": 0}
    for d, services in jours.items():
        if d.year != annee or d.month != mois:
            continue
        if any(code_affiche(sv["code"]).upper() in CODES_REPOS for sv in services if sv.get("code")):
            r["repos"] += 1
        if d in greves:
            continue
        durees = [x for x in (duree_service(sv) for sv in services) if x is not None]
        if not durees:
            continue
        r["travailles"] += 1
        r["minutes"] += sum(durees)
        if any(est_nuit(sv) for sv in services):
            r["nuits"] += 1
        if d.weekday() == 6:
            r["dimanches"] += 1
        if nom_ferie(d):
            r["feries"] += 1
    return r


def heures_txt(minutes):
    return f"{minutes // 60} h {minutes % 60:02d}"


# ---------------------------------------------------------------------------
#  Changements entre deux commandes
# ---------------------------------------------------------------------------
_R_PLAGES = re.compile(r"(\d{1,2}:\d{2})\s*[–-]\s*(\d{1,2}:\d{2})")


def horaire_court(sv):
    """« 13:28–22:35 » (PS–FS, sinon du début de la 1re plage à la fin de la dernière)."""
    h = sv.get("horaires", "")
    ps, fs = _R_PS.search(h), _R_FS.search(h)
    if ps and fs:
        return f"{ps.group(1)}:{ps.group(2)}–{fs.group(1)}:{fs.group(2)}"
    plages = _R_PLAGES.findall(h)
    return f"{plages[0][0]}–{plages[-1][1]}" if plages else ""


def resume_services(services):
    """Résumé d'une journée venant des bulletins : « S-VAL 13:28–22:35 + K 12:00–13:00 »."""
    codes_a_jour()                          # noms affichés à jour (codes.txt)
    parts = []
    for sv in services:
        if sv.get("source") in ("manuel", "auto") or not sv.get("code"):
            continue
        h = horaire_court(sv)
        parts.append(nom_affiche(sv) + (" " + h if h else ""))
    return " + ".join(parts)


def texte_changement(d, avant, apres):
    return f"{JOURS[d.weekday()][:3].capitalize()} {d.strftime('%d/%m/%Y')} : {avant or '(rien)'} → {apres or '(rien)'}"


# ---------------------------------------------------------------------------
#  Export vers un agenda (.ics : Google Agenda, Outlook, agenda du téléphone)
# ---------------------------------------------------------------------------
_VTIMEZONE_PARIS = [
    "BEGIN:VTIMEZONE", "TZID:Europe/Paris",
    "BEGIN:DAYLIGHT", "TZOFFSETFROM:+0100", "TZOFFSETTO:+0200", "TZNAME:CEST",
    "DTSTART:19700329T020000", "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "END:DAYLIGHT",
    "BEGIN:STANDARD", "TZOFFSETFROM:+0200", "TZOFFSETTO:+0100", "TZNAME:CET",
    "DTSTART:19701025T030000", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "END:STANDARD",
    "END:VTIMEZONE"]


def _ics_texte(t):
    return t.replace("\\", "\\\\").replace(";", "\;").replace(",", "\\,").replace("\n", "\\n")


def _ics_plier(ligne):
    """Coupe les lignes à 75 octets (norme iCalendar), sans couper un caractère."""
    res, cour, taille, limite = [], "", 0, 75
    for ch in ligne:
        n = len(ch.encode("utf-8"))
        if taille + n > limite:
            res.append(cour)
            cour, taille, limite = " ", 1, 75
        cour += ch
        taille += n
    res.append(cour)
    return res


def texte_ics(jours, greves=(), depuis=None, horodatage=None):
    """Calendrier iCalendar des services (un événement par service ; journée entière sans horaires)."""
    codes_a_jour()
    stamp = horodatage or dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    L = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Planning PDF//Planning Commandes//FR",
         "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:Planning", "X-WR-TIMEZONE:Europe/Paris"]
    L += _VTIMEZONE_PARIS
    aff = pour_affichage(jours)
    for d in sorted(aff):
        if depuis and d < depuis:
            continue
        for k, sv in enumerate(aff[d]):
            if not sv.get("code") and not sv.get("libelle"):
                continue
            nom = nom_affiche(sv) if sv.get("code") else sv["libelle"]
            if d in greves:
                nom += " (grève)"
            h = sv.get("horaires", "")
            ps, fs = _R_PS.search(h), _R_FS.search(h)
            plages = _R_PLAGES.findall(h)
            if ps and fs:
                debut, fin = f"{int(ps.group(1)):02d}{ps.group(2)}", f"{int(fs.group(1)):02d}{fs.group(2)}"
            elif plages:
                debut, fin = (plages[0][0].zfill(5).replace(":", ""), plages[-1][1].zfill(5).replace(":", ""))
            else:
                debut = fin = None
            L += ["BEGIN:VEVENT", f"UID:{d.strftime('%Y%m%d')}-{k}-{re.sub(r'[^A-Za-z0-9]', '', sv.get('code') or 'note')}@planning-commandes",
                  f"DTSTAMP:{stamp}"]
            if debut:
                jour_fin = d + dt.timedelta(days=1) if fin <= debut else d
                L += [f"DTSTART;TZID=Europe/Paris:{d.strftime('%Y%m%d')}T{debut}00",
                      f"DTEND;TZID=Europe/Paris:{jour_fin.strftime('%Y%m%d')}T{fin}00"]
            else:
                L += [f"DTSTART;VALUE=DATE:{d.strftime('%Y%m%d')}",
                      f"DTEND;VALUE=DATE:{(d + dt.timedelta(days=1)).strftime('%Y%m%d')}"]
            desc = lignes_horaires(h)
            if sv.get("libelle"):
                desc.append(sv["libelle"])
            L += [f"SUMMARY:{_ics_texte(nom)}", "TRANSP:OPAQUE" if debut else "TRANSP:TRANSPARENT"]
            if desc:
                L.append("DESCRIPTION:" + _ics_texte("\n".join(x.replace("     ", "  ") for x in desc)))
            L.append("END:VEVENT")
    L.append("END:VCALENDAR")
    return "\r\n".join(x for l in L for x in _ics_plier(l)) + "\r\n"


def _pdf_des_mois(planning, liste_mois):
    """PdfWriter contenant une page A4 paysage par mois. Les pages sont dessinées
    une par une (une seule image en mémoire à la fois, même avec des années de planning)."""
    from pypdf import PdfReader, PdfWriter
    writer = PdfWriter()
    for a, m in sorted(liste_mois):
        img, _ = dessiner_planning(a, m, planning.jours, *A4, planning.agent, planning.greves)
        tampon = io.BytesIO()
        img.save(tampon, "PDF", resolution=300.0)
        del img
        tampon.seek(0)
        writer.append(PdfReader(tampon))
    writer.add_metadata({"/Title": "Planning", "/Author": planning.agent or "", "/Creator": "Planning PDF"})
    return writer


def pdf_impression(planning, liste_mois, chemin=None):
    """PDF A4 paysage des mois choisis (une page par mois), pour l'impression."""
    chemin = chemin or os.path.join(tempfile.gettempdir(), "PlanningPDF_impression.pdf")
    writer = _pdf_des_mois(planning, liste_mois)
    with open(chemin, "wb") as f:
        writer.write(f)
    return chemin


def imprimer_fichier(chemin):
    """Envoie un PDF à l'imprimante par défaut. Renvoie True si l'envoi a pu être lancé."""
    import subprocess
    try:
        if sys.platform.startswith("win"):
            os.startfile(chemin, "print")      # via le lecteur PDF par défaut
        else:
            subprocess.run(["lp", chemin], check=True, capture_output=True)
        return True
    except Exception:
        return False


def exporter_pdf(chemin, planning, en_plus=None):
    """
    PDF avec une page par mois contenant des informations. Les données du
    planning sont jointes au PDF : en le rouvrant avec « Ouvrir… », on
    retrouve le planning et on peut continuer à le compléter.
    """
    writer = _pdf_des_mois(planning, mois_du_planning(planning, en_plus))
    writer.add_attachment(NOM_PIECE_JOINTE, json.dumps(planning.vers_dict(), ensure_ascii=False).encode("utf-8"))

    # écriture dans un fichier temporaire puis remplacement : le PDF existant
    # n'est jamais abîmé si quelque chose se passe mal
    dossier = os.path.dirname(os.path.abspath(chemin))
    fd, tmp = tempfile.mkstemp(suffix=".pdf", dir=dossier)
    try:
        with os.fdopen(fd, "wb") as f:
            writer.write(f)
        os.replace(tmp, chemin)
    except PermissionError:
        os.remove(tmp)
        raise PermissionError(f"Le fichier « {os.path.basename(chemin)} » est ouvert dans un autre "
                              "programme. Fermez-le puis réessayez.")
    except Exception:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise


def lire_planning_exporte(chemin):
    """Si le PDF est un planning exporté par ce programme, renvoie son Planning, sinon None."""
    try:
        from pypdf import PdfReader
        pj = PdfReader(chemin).attachments.get(NOM_PIECE_JOINTE)
    except Exception:
        return None
    if not pj:
        return None
    return Planning.depuis_dict(json.loads(pj[0].decode("utf-8")))


# ---------------------------------------------------------------------------
#  Fusion de plusieurs bulletins
# ---------------------------------------------------------------------------
class Planning:
    def __init__(self):
        self.jours = {}          # date -> [service]
        self.edition = {}        # date -> datetime du bulletin qui a fourni la date
        self.agent = ""
        self.sortie = ""         # PDF de sortie mis à jour automatiquement
        self.importes = {}       # empreinte md5 -> fichier déjà lu dans le dossier « commande »
        self.version_lecture = VERSION_LECTURE
        self.greves = set()      # jours de grève (filigrane « GRÈVE »)

    def integrer(self, info, changements=None):
        """Le bulletin le plus récent remplace les jours qu'il couvre.
        Renvoie (jours mis à jour, jours ignorés car bulletin plus ancien).
        changements : liste complétée par (date, avant, après) pour chaque jour déjà
        connu dont le contenu change."""
        ed = info["edition"] or dt.datetime.min
        maj, ignores = 0, 0
        for date, services in sorted(info["jours"].items()):
            if date in self.edition and self.edition[date] > ed:
                ignores += 1
                continue
            if changements is not None:
                avant, apres = resume_services(self.jours.get(date, [])), resume_services(services)
                if avant and avant != apres:
                    changements.append((date, avant, apres))
            manuels = [s for s in self.jours.get(date, []) if s["source"] == "manuel"]
            for sv in services:
                sv["fichier"] = info.get("fichier", "")
                sv["empreinte"] = info.get("empreinte", "")
            self.jours[date] = services + manuels
            self.edition[date] = ed
            maj += 1
        if info["agent"]:
            self.agent = info["agent"]
        return maj, ignores

    def supprimer(self, date, i):
        del self.jours[date][i]
        if not self.jours[date]:
            del self.jours[date]
            self.edition.pop(date, None)

    def fusionner(self, autre):
        """Ajoute un autre planning (ex. PDF de sortie rouvert) ; le plus récent gagne."""
        n = 0
        for date, services in autre.jours.items():
            ed_a = autre.edition.get(date, dt.datetime.min)
            if date in self.jours and self.edition.get(date, dt.datetime.min) > ed_a:
                continue
            existants = [s for s in self.jours.get(date, []) if s["source"] == "manuel"]
            self.jours[date] = services + [s for s in existants if s not in services]
            self.edition[date] = ed_a
            n += 1
        if autre.agent and not self.agent:
            self.agent = autre.agent
        self.greves |= getattr(autre, "greves", set())
        return n

    # ---- sauvegarde ----------------------------------------------------
    def vers_dict(self):
        return {
            "version": 1,
            "agent": self.agent,
            "sortie": getattr(self, "sortie", ""),
            "importes": getattr(self, "importes", {}),
            "version_lecture": getattr(self, "version_lecture", 0),
            "greves": sorted(d.isoformat() for d in getattr(self, "greves", ())),
            "jours": {d.isoformat(): v for d, v in sorted(self.jours.items())},
            "edition": {d.isoformat(): (e.isoformat() if e > dt.datetime.min else None)
                        for d, e in self.edition.items()},
        }

    @classmethod
    def depuis_dict(cls, data):
        p = cls()
        p.agent = data.get("agent", "")
        p.sortie = data.get("sortie", "")
        p.importes = dict(data.get("importes", {}))
        p.version_lecture = data.get("version_lecture", 0)
        p.greves = {dt.date.fromisoformat(d) for d in data.get("greves", [])}
        for d, v in data.get("jours", {}).items():
            p.jours[dt.date.fromisoformat(d)] = [
                dict(service(s.get("code", ""), s.get("horaires", ""), s.get("libelle", ""),
                             s.get("source", "manuel"), s.get("intitule", "")),
                     fichier=s.get("fichier", ""), empreinte=s.get("empreinte", "")) for s in v]
        for d, e in data.get("edition", {}).items():
            p.edition[dt.date.fromisoformat(d)] = dt.datetime.fromisoformat(e) if e else dt.datetime.min
        return p

    def sauver(self, chemin):
        tmp = chemin + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.vers_dict(), f, ensure_ascii=False, indent=1)
        os.replace(tmp, chemin)

    @classmethod
    def charger(cls, chemin):
        with open(chemin, encoding="utf-8") as f:
            return cls.depuis_dict(json.load(f))


# ---------------------------------------------------------------------------
#  Interface graphique (Tkinter)
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
#  Sauvegardes du planning (restaurables)
# ---------------------------------------------------------------------------
NB_SAUVEGARDES_MAX = 40


def dossier_sauvegardes():
    try:
        base = dossier_donnees()
    except OSError:
        base = APP_DIR
    d = os.path.join(base, "sauvegardes")
    os.makedirs(d, exist_ok=True)
    return d


def creer_sauvegarde(planning, motif="manuelle"):
    """Enregistre une copie complète du planning. Renvoie le chemin du fichier."""
    maintenant = dt.datetime.now()
    nom = maintenant.strftime("planning_%Y-%m-%d_%Hh%M-%S.json")
    chemin = os.path.join(dossier_sauvegardes(), nom)
    data = {"sauvegarde": {"date": maintenant.isoformat(timespec="seconds"), "motif": motif,
                           "jours": len(planning.jours)},
            "planning": planning.vers_dict()}
    try:
        with open(chemin_codes(), encoding="utf-8-sig") as f:
            data["codes_txt"] = f.read()             # copie de codes.txt, pour information
    except (OSError, UnicodeDecodeError):
        pass
    with open(chemin, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    # on ne garde que les plus récentes
    tous = sorted(f for f in os.listdir(dossier_sauvegardes()) if f.startswith("planning_") and f.endswith(".json"))
    for f in tous[:-NB_SAUVEGARDES_MAX]:
        try:
            os.remove(os.path.join(dossier_sauvegardes(), f))
        except OSError:
            pass
    return chemin


def lister_sauvegardes():
    """[(chemin, date, motif, nb jours)] du plus récent au plus ancien."""
    res = []
    d = dossier_sauvegardes()
    for f in sorted(os.listdir(d), reverse=True):
        if not (f.startswith("planning_") and f.endswith(".json")):
            continue
        chemin = os.path.join(d, f)
        try:
            with open(chemin, encoding="utf-8") as fh:
                info = json.load(fh).get("sauvegarde", {})
            date = dt.datetime.fromisoformat(info.get("date"))
        except Exception:
            continue
        res.append((chemin, date, info.get("motif", ""), info.get("jours", 0)))
    return res


def lire_sauvegarde(chemin):
    with open(chemin, encoding="utf-8") as f:
        return Planning.depuis_dict(json.load(f)["planning"])


def lancer_interface():
    import tkinter as tk
    from tkinter import filedialog, messagebox, simpledialog, ttk
    from PIL import ImageTk

    class App(tk.Tk):
        def __init__(self):
            super().__init__()
            self.title("Planning PDF")
            self.geometry("1400x820")
            self.minsize(900, 560)
            auj = dt.date.today()
            self.annee, self.mois = auj.year, auj.month
            self.cases, self._photo, self._apres = {}, None, None
            try:
                self.dernier_dossier = dossier_commandes()
            except OSError:
                self.dernier_dossier = os.path.expanduser("~")
            self.modifie = False          # modifs pas encore reportées dans le PDF de sortie
            self._occupe = False          # évite deux actualisations en même temps

            # planning mémorisé lors de la dernière utilisation
            try:
                self.fichier_etat = os.path.join(dossier_donnees(), FICHIER_ETAT)
            except OSError:
                self.fichier_etat = os.path.join(APP_DIR, FICHIER_ETAT)
            self.pl = Planning()
            if os.path.exists(self.fichier_etat):
                try:
                    self.pl = Planning.charger(self.fichier_etat)
                except Exception:
                    pass
            if self.pl.jours:
                dmax = max(self.pl.jours)
                self.annee, self.mois = dmax.year, dmax.month
            if self.pl.sortie:
                self.dernier_dossier = os.path.dirname(self.pl.sortie)

            self.appliquer_theme()
            self.appliquer_icone()
            self.creer_menus()

            # ---- bandeau du mois : navigation + actions rapides
            bandeau = ttk.Frame(self, padding=(12, 10, 12, 4))
            bandeau.pack(fill="x")
            nav = ttk.Frame(bandeau)
            nav.pack(side="left")
            ttk.Button(nav, text="◀", width=3, command=lambda: self.decaler(-1)).pack(side="left")
            ttk.Button(nav, text="▶", width=3, command=lambda: self.decaler(1)).pack(side="left", padx=(4, 0))
            self.lbl_mois = ttk.Label(nav, font=(self.police_ui, 20, "bold"), width=16, anchor="w")
            self.lbl_mois.pack(side="left", padx=(14, 8))
            ttk.Button(nav, text="Aujourd'hui", command=self.mois_courant).pack(side="left")

            actions = ttk.Frame(bandeau)
            actions.pack(side="right")
            for texte, cmd, style in (("＋  Ajouter une commande", self.ouvrir, "Accent.TButton"),
                                      ("⎙  Imprimer", self.choisir_impression, None),
                                      ("⟳  Mettre à jour le PDF", self.maj_sortie, None)):
                b = ttk.Button(actions, text=texte, command=cmd)
                if style and self.theme_moderne:
                    b.configure(style=style)
                b.pack(side="left", padx=(6, 0))
                if cmd == self.maj_sortie:
                    self.btn_maj = b

            # ---- résumé du mois en pastilles
            self.resume = tk.Frame(self, padx=12, pady=4)
            self.resume.pack(fill="x")

            # ---- barre d'état (placée avant le planning pour rester visible)
            self.statut = ttk.Label(self, anchor="w", padding=(12, 5), text=(
                "Ouvrez un bulletin (Ctrl+O) ou déposez-le dans le dossier « commande ». "
                "Clic sur un jour : détail ; clic droit : grève, note."))
            self.statut.pack(fill="x", side="bottom")
            ttk.Separator(self, orient="horizontal").pack(fill="x", side="bottom")

            corps = self.corps = ttk.PanedWindow(self, orient="horizontal")
            corps.pack(fill="both", expand=True)
            self.canvas = tk.Canvas(corps, bg=self.fond_canvas, highlightthickness=0)
            corps.add(self.canvas, weight=4)

            droite = self.panneau_infos = ttk.Frame(corps, padding=(6, 0, 6, 6))
            corps.add(droite, weight=1)
            ttk.Label(droite, text="Informations extraites (double-clic pour modifier)").pack(anchor="w")
            self.arbre = ttk.Treeview(droite, columns=("date", "code", "horaires", "libelle"),
                                      show="headings")
            for c, t, w in (("date", "Date", 80), ("code", "Code", 70),
                            ("horaires", "Horaires", 100), ("libelle", "Libellé", 200)):
                self.arbre.heading(c, text=t)
                self.arbre.column(c, width=w, stretch=(c == "libelle"))
            sb = ttk.Scrollbar(droite, orient="vertical", command=self.arbre.yview)
            self.arbre.configure(yscrollcommand=sb.set)
            self.arbre.pack(side="left", fill="both", expand=True)
            sb.pack(side="right", fill="y")
            self.arbre.bind("<<TreeviewSelect>>", self.aller_selection)
            self.arbre.bind("<Delete>", lambda e: self.supprimer())
            self.arbre.bind("<Double-1>", self.modifier)

            self.canvas.bind("<Configure>", lambda e: self.planifier_rendu())
            self.canvas.bind("<Button-1>", self.clic_case)
            self.canvas.bind("<Button-3>", self.menu_case)          # clic droit
            # planning interactif : survol, info-bulle, molette
            self.canvas.bind("<Motion>", self.survol)
            self.canvas.bind("<Leave>", lambda e: self.fin_survol())
            self.bind("<MouseWheel>", self.molette)
            self.canvas.bind("<Button-4>", lambda e: self.decaler(-1))     # molette Linux
            self.canvas.bind("<Button-5>", lambda e: self.decaler(1))
            self._survol, self._bulle, self._bulle_minuteur = None, None, None
            if sys.platform == "darwin":
                self.canvas.bind("<Button-2>", self.menu_case)
            self.protocol("WM_DELETE_WINDOW", self.quitter)
            self.bind("<FocusIn>", self.verifier_codes)     # codes.txt modifié pendant ce temps ?
            codes_a_jour()
            self.after(300, lambda: self.actualiser(silencieux=True))   # range + lit le dossier « commande »
            self._maj_prete = False                                     # nouvelle version téléchargée ?
            self.after(4000, lambda: self.verifier_maj(manuel=False))    # nouvelle version de l'exe ? (1 fois/jour)
            self._mail_en_cours = False
            self.after(6000, self._minuteur_mail)                       # commandes reçues par mail (toutes les heures)
            self.tout_rafraichir(sauver=False)
            self.afficher_sortie()
            self.appliquer_panneau()                  # cadre « Informations extraites » masqué par défaut

        # ---- affichage ---------------------------------------------------
        def planifier_rendu(self):
            if self._apres:
                self.after_cancel(self._apres)
            self._apres = self.after(60, self.rendre)

        def rendre(self):
            self._apres = None
            cw, ch = self.canvas.winfo_width(), self.canvas.winfo_height()
            if cw < 50 or ch < 50:
                return
            ratio = 3508 / 2480
            w = min(cw - 20, (ch - 20) * ratio)
            h = w / ratio
            img, cases = dessiner_planning(self.annee, self.mois, self.pl.jours, w, h, self.pl.agent,
                                           self.pl.greves, theme=_theme_courant["ecran"])
            ox, oy = (cw - w) / 2, (ch - h) / 2
            self.cases = {k: (a + ox, b + oy, c + ox, e + oy) for k, (a, b, c, e) in cases.items()}
            self._photo = ImageTk.PhotoImage(img)
            self.canvas.delete("all")
            self.canvas.create_image(ox, oy, image=self._photo, anchor="nw")
            self._survol = None
            auj = dt.date.today()
            if auj in self.cases:                   # aujourd'hui encadré (à l'écran seulement)
                a, b, c, d_ = self.cases[auj]
                self.canvas.create_rectangle(a + 2, b + 2, c - 2, d_ - 2, outline=self.accent, width=3,
                                             tags="aujourdhui")
            self.lbl_mois.config(text=f"{MOIS_LONG[self.mois - 1].capitalize()} {self.annee}")
            self.maj_resume()

        def tout_rafraichir(self, sauver=True):
            if sauver:
                self.modifie = True
                try:
                    self.pl.sauver(self.fichier_etat)
                except OSError:
                    pass
            self.arbre.delete(*self.arbre.get_children())
            for date in sorted(self.pl.jours):
                for i, s in enumerate(self.pl.jours[date]):
                    self.arbre.insert("", "end", iid=f"{date.isoformat()}|{i}", values=(
                        date.strftime("%d/%m/%Y"), nom_affiche(s), s["horaires"], s["libelle"]))
            self.planifier_rendu()

        def decaler(self, n):
            m = self.mois - 1 + n
            self.annee, self.mois = self.annee + m // 12, m % 12 + 1
            self.planifier_rendu()

        def _selection(self):
            out = []
            for iid in self.arbre.selection():
                ds, i = iid.split("|")
                out.append((dt.date.fromisoformat(ds), int(i)))
            return out

        def aller_selection(self, _e=None):
            sel = self._selection()
            if sel and (sel[0][0].year, sel[0][0].month) != (self.annee, self.mois):
                self.annee, self.mois = sel[0][0].year, sel[0][0].month
                self.planifier_rendu()

        # ---- actions -----------------------------------------------------
        def ouvrir(self):
            chemins = filedialog.askopenfilenames(
                title="Choisir un ou plusieurs bulletins PDF", initialdir=dossier_commandes(),
                filetypes=[("Documents PDF", "*.pdf *.PDF"), ("Tous les fichiers", "*.*")])
            if not chemins:
                return
            self.dernier_dossier = os.path.dirname(chemins[0])
            self.config(cursor="watch")
            self.update_idletasks()
            infos, erreurs, copies = [], [], []
            plannings = []
            for c in chemins:
                ancien = lire_planning_exporte(c)
                if ancien is not None:              # PDF de sortie déjà généré
                    plannings.append((c, ancien))
                    continue
                try:
                    info = lire_bulletin(c)
                    if not info["jours"]:
                        raise ValueError("aucun jour trouvé dans le tableau")
                    infos.append(info)
                    try:
                        self.pl.importes[_empreinte(c)] = os.path.basename(c)   # déjà lu
                    except OSError:
                        pass
                    try:                              # copie + renommage dans « commande »
                        n = ajouter_au_dossier(c)
                        if n:
                            copies.append(n)
                    except Exception as ex:
                        erreurs.append(f"• {os.path.basename(c)} : lu, mais pas copié dans « commande » ({ex})")
                except Exception as ex:
                    erreurs.append(f"• {os.path.basename(c)} : {ex}")
            self.config(cursor="")
            # du plus ancien au plus récent : le plus récent a le dernier mot
            infos.sort(key=lambda i: i["edition"] or dt.datetime.min)
            maj = ign = 0
            for c, ancien in plannings:
                maj += self.pl.fusionner(ancien)
                self.pl.sortie = c                 # c'est ce PDF qui sera complété
            changements = []
            for info in infos:
                a, b = self.pl.integrer(info, changements)
                maj, ign = maj + a, ign + b
            if infos:
                cpt = Counter((d.year, d.month) for i in infos for d in i["jours"])
                self.annee, self.mois = cpt.most_common(1)[0][0]
            elif plannings and self.pl.jours:
                dmax = max(self.pl.jours)
                self.annee, self.mois = dmax.year, dmax.month
            if infos or plannings:
                self.tout_rafraichir()
            msg = f"{len(infos)} bulletin(s) lu(s), {maj} jour(s) mis à jour."
            if plannings:
                self.afficher_sortie()
                msg = (f"Planning rechargé depuis {os.path.basename(self.pl.sortie)} "
                       f"({len(self.pl.jours)} jours). Les prochains bulletins y seront ajoutés.")
                if infos:
                    msg += f" {len(infos)} bulletin(s) lu(s)."
            if ign:
                msg += f" {ign} jour(s) ignoré(s) : un bulletin plus récent était déjà chargé."
            if copies:
                msg += f" {len(copies)} copié(s) dans « commande »."
            self.statut.config(text=msg)
            if erreurs:
                messagebox.showwarning("Fichiers non lus", "\n".join(erreurs))
            if changements:
                self.montrer_changements(changements)
            # le PDF de sortie déjà généré est complété automatiquement
            if infos and self.pl.sortie:
                self.maj_sortie(msg_avant=msg + " ")

        def nouveau(self):
            """Zone de danger : effacer le planning (avec sauvegarde automatique)."""
            fen = tk.Toplevel(self)
            fen.title("Zone de danger")
            fen.transient(self)
            fen.resizable(False, False)
            fen.configure(bg="#fdecea")
            cadre = tk.Frame(fen, bg="#fdecea", padx=18, pady=14)
            cadre.pack(fill="both", expand=True)
            tk.Label(cadre, text="⚠  ZONE DE DANGER", font=("", 15, "bold"), fg="#b00020",
                     bg="#fdecea").pack(anchor="w")
            tk.Label(cadre, bg="#fdecea", justify="left", wraplength=460, font=("", 10), text=(
                "« Nouveau » va EFFACER tout le planning :\n"
                "   • toutes les notes ajoutées à la main ;\n"
                "   • toutes les corrections faites dans la liste ;\n"
                "   • le lien avec votre PDF de sortie.\n\n"
                "Le planning sera ensuite reconstruit à partir des bulletins du dossier « commande ».\n"
                "Ne sont PAS touchés : le dossier « commande », codes.txt et le PDF déjà créé.\n\n"
                "Une sauvegarde est créée automatiquement juste avant : en cas d'erreur, "
                "restaurez-la avec le bouton « Sauvegardes… ».")).pack(anchor="w", pady=(8, 10))
            tk.Label(cadre, text="Pour confirmer, tapez EFFACER :", bg="#fdecea",
                     font=("", 10, "bold")).pack(anchor="w")
            saisie = tk.StringVar()
            champ = ttk.Entry(cadre, textvariable=saisie, width=20)
            champ.pack(anchor="w", pady=(2, 10))
            boutons = tk.Frame(cadre, bg="#fdecea")
            boutons.pack(fill="x")

            def effacer():
                if saisie.get().strip().upper() != "EFFACER":
                    return
                try:
                    sauvegarde = creer_sauvegarde(self.pl, "avant « Nouveau »")
                except Exception as ex:
                    messagebox.showerror("Zone de danger", "La sauvegarde a échoué, rien n'a été effacé.\n"
                                                           f"{ex}", parent=fen)
                    return
                fen.destroy()
                self.pl = Planning()
                self.tout_rafraichir()
                self.modifie = False
                self.afficher_sortie()
                self.actualiser()
                self.statut.config(text="Planning reconstruit à partir du dossier « commande ». Sauvegarde "
                                        f"de l'ancien planning : {os.path.basename(sauvegarde)} "
                                        "(bouton « Sauvegardes… » pour la restaurer).")

            bouton_effacer = tk.Button(boutons, text="Effacer le planning", fg="white", bg="#b00020",
                                       activebackground="#8a0019", activeforeground="white",
                                       disabledforeground="#f3b5bf", state="disabled", command=effacer)
            bouton_effacer.pack(side="left")
            ttk.Button(boutons, text="Annuler", command=fen.destroy).pack(side="right")
            saisie.trace_add("write", lambda *a: bouton_effacer.config(
                state="normal" if saisie.get().strip().upper() == "EFFACER" else "disabled"))
            champ.bind("<Return>", lambda e: effacer())
            fen.bind("<Escape>", lambda e: fen.destroy())
            champ.focus_set()
            fen.grab_set()

        def fenetre_sauvegardes(self):
            """Liste des sauvegardes : créer, restaurer, ouvrir le dossier."""
            fen = tk.Toplevel(self)
            fen.title("Sauvegardes du planning")
            fen.transient(self)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text="Sauvegardes du planning", font=("", 12, "bold")).pack(anchor="w")
            ttk.Label(cadre, foreground="#666", text="Une sauvegarde est faite automatiquement avant "
                      "« Nouveau » et avant chaque restauration.").pack(anchor="w", pady=(0, 6))
            arbre = ttk.Treeview(cadre, columns=("date", "motif", "jours"), show="headings", height=10)
            for c, t, w in (("date", "Date", 150), ("motif", "Motif", 220), ("jours", "Jours", 60)):
                arbre.heading(c, text=t)
                arbre.column(c, width=w, anchor="center" if c == "jours" else "w")
            arbre.pack(fill="both", expand=True)
            chemins = {}

            def remplir():
                arbre.delete(*arbre.get_children())
                chemins.clear()
                for k, (ch, date, motif, nb) in enumerate(lister_sauvegardes()):
                    iid = f"s{k}"
                    chemins[iid] = ch
                    arbre.insert("", "end", iid=iid, values=(date.strftime("%d/%m/%Y %H:%M:%S"), motif, nb))

            def creer():
                try:
                    creer_sauvegarde(self.pl, "manuelle")
                except Exception as ex:
                    messagebox.showerror("Sauvegardes", str(ex), parent=fen)
                    return
                remplir()
                self.statut.config(text="Sauvegarde du planning créée.")

            def restaurer():
                sel = arbre.selection()
                if not sel:
                    messagebox.showinfo("Sauvegardes", "Sélectionnez une sauvegarde dans la liste.", parent=fen)
                    return
                ch = chemins[sel[0]]
                if not messagebox.askyesno("Restaurer", "Remplacer le planning actuel par cette sauvegarde ?\n"
                                           "(Le planning actuel sera d'abord sauvegardé.)", parent=fen):
                    return
                try:
                    nouveau = lire_sauvegarde(ch)
                    creer_sauvegarde(self.pl, "avant restauration")
                except Exception as ex:
                    messagebox.showerror("Sauvegardes", f"Restauration impossible :\n{ex}", parent=fen)
                    return
                self.pl = nouveau
                if self.pl.jours:
                    dmax = max(self.pl.jours)
                    self.annee, self.mois = dmax.year, dmax.month
                self.tout_rafraichir()
                self.afficher_sortie()
                fen.destroy()
                self.statut.config(text=f"Sauvegarde restaurée ({len(self.pl.jours)} jours). "
                                        "Cliquez sur « Mettre à jour le PDF » si besoin.")

            boutons = ttk.Frame(cadre, padding=(0, 8, 0, 0))
            boutons.pack(fill="x")
            ttk.Button(boutons, text="Créer une sauvegarde", command=creer).pack(side="left")
            ttk.Button(boutons, text="Restaurer", command=restaurer).pack(side="left", padx=4)
            ttk.Button(boutons, text="Ouvrir le dossier",
                       command=lambda: self.ouvrir_fichier(dossier_sauvegardes())).pack(side="left")
            ttk.Button(boutons, text="Fermer", command=fen.destroy).pack(side="right")
            arbre.bind("<Double-1>", lambda e: restaurer())
            fen.bind("<Escape>", lambda e: fen.destroy())
            remplir()
            fen.grab_set()

        def _ajouter_a(self, date):
            t = simpledialog.askstring("Ajouter une note", f"Note pour le {date.strftime('%d/%m/%Y')} :",
                                       parent=self)
            if t and t.strip():
                self.pl.jours.setdefault(date, []).append(service(libelle=t.strip()))
                self.tout_rafraichir()

        def ajouter(self):
            s = simpledialog.askstring("Ajouter une note", "Date (JJ/MM/AAAA) :", parent=self,
                                       initialvalue=f"01/{self.mois:02d}/{self.annee}")
            if not s:
                return
            try:
                date = dt.datetime.strptime(s.strip(), "%d/%m/%Y").date()
            except ValueError:
                messagebox.showerror("Date invalide", "Format attendu : JJ/MM/AAAA")
                return
            self.annee, self.mois = date.year, date.month
            self._ajouter_a(date)
            self.planifier_rendu()

        def modifier(self, _e=None):
            sel = self._selection()
            if not sel:
                if _e is None:                   # appelé depuis le menu
                    messagebox.showinfo("Modifier", "Sélectionnez d'abord une ligne dans la liste "
                                        "(menu Affichage > Informations extraites).")
                return
            date, i = sel[0]
            s = self.pl.jours[date][i]
            for champ, nom in (("code", "Code"), ("horaires", "Horaires"), ("libelle", "Libellé")):
                v = simpledialog.askstring("Modifier", f"{nom} ({date.strftime('%d/%m/%Y')}) :",
                                           parent=self, initialvalue=s[champ])
                if v is None:
                    break
                s[champ] = v.strip()
            if not any(s[k] for k in ("code", "horaires", "libelle")):
                self.pl.supprimer(date, i)
            self.tout_rafraichir()

        def supprimer(self):
            sel = self._selection()
            if not sel:
                messagebox.showinfo("Supprimer", "Sélectionnez d'abord une ligne dans la liste "
                                    "(menu Affichage > Informations extraites).")
                return
            for date, i in sorted(sel, key=lambda x: -x[1]):
                self.pl.supprimer(date, i)
            self.tout_rafraichir()

        def clic_case(self, e):
            self.masquer_bulle()
            for date, (a, b, c, d_) in self.cases.items():
                if a <= e.x <= c and b <= e.y <= d_:
                    self.detail_jour(date)
                    return

        def ouvrir_fichier(self, chemin):
            import subprocess
            try:
                if sys.platform.startswith("win"):
                    os.startfile(chemin)
                elif sys.platform == "darwin":
                    subprocess.Popen(["open", chemin])
                else:
                    subprocess.Popen(["xdg-open", chemin])
            except Exception as ex:
                messagebox.showerror("Ouvrir le PDF", f"Impossible d'ouvrir le fichier :\n{ex}")

        def case_sous(self, e):
            for date, (a, b, c, d_) in self.cases.items():
                if a <= e.x <= c and b <= e.y <= d_:
                    return date
            return None

        def menu_case(self, e):
            """Clic droit sur une case : grève, note, détail."""
            self.masquer_bulle()
            date = self.case_sous(e)
            if date is None:
                return
            m = tk.Menu(self, tearoff=False)
            titre = f"{JOURS[date.weekday()].capitalize()} {date.day} {MOIS_LONG[date.month - 1]}"
            m.add_command(label=titre, state="disabled")
            m.add_separator()
            if date in self.pl.greves:
                m.add_command(label="Retirer le jour de grève", command=lambda: self.basculer_greve(date))
            else:
                m.add_command(label="Ajouter un jour de grève", command=lambda: self.basculer_greve(date))
            m.add_command(label="Ajouter une note…", command=lambda: self._ajouter_a(date))
            m.add_command(label="Détail du jour…", command=lambda: self.detail_jour(date))
            try:
                m.tk_popup(e.x_root, e.y_root)
            finally:
                m.grab_release()

        def basculer_greve(self, date):
            if date in self.pl.greves:
                self.pl.greves.discard(date)
                msg = f"Jour de grève retiré : {date.strftime('%d/%m/%Y')}."
            else:
                self.pl.greves.add(date)
                msg = f"Jour de grève ajouté : {date.strftime('%d/%m/%Y')}."
            self.tout_rafraichir()
            self.statut.config(text=msg + " Cliquez sur « Mettre à jour le PDF » pour le reporter dans le PDF.")

        def detail_jour(self, date):
            """Fenêtre : ce qui est prévu ce jour-là et de quel bulletin PDF cela vient."""
            fen = tk.Toplevel(self)
            fen.title(f"{JOURS[date.weekday()].capitalize()} {date.day} {MOIS_LONG[date.month - 1]} {date.year}")
            fen.transient(self)
            fen.resizable(False, False)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text=fen.title(), font=("", 13, "bold")).pack(anchor="w", pady=(0, 8))
            if date in self.pl.greves:
                ttk.Label(cadre, text="⚑ Jour de grève", foreground="#c00000",
                          font=("", 11, "bold")).pack(anchor="w", pady=(0, 6))
            if nom_ferie(date):
                ttk.Label(cadre, text=f"Jour férié : {nom_ferie(date)}", foreground="#b0463f",
                          font=("", 11, "bold")).pack(anchor="w", pady=(0, 6))

            services = avec_dn(self.pl.jours).get(date, [])
            if not services:
                ttk.Label(cadre, text="Aucune information pour ce jour.").pack(anchor="w")
            deja = set()
            for sv in services:
                bloc = ttk.Frame(cadre, padding=(0, 4))
                bloc.pack(fill="x", anchor="w")
                titre = nom_affiche(sv) if sv["code"] else "Note"
                if sv["code"] and nom_affiche(sv) != sv["code"]:
                    titre += f"   (code {sv['code']})"
                ttk.Label(bloc, text=titre, font=("", 11, "bold")).pack(anchor="w")
                for h in [x.strip() for x in sv.get("horaires", "").split("/") if x.strip()]:
                    ttk.Label(bloc, text="    " + h).pack(anchor="w")
                if sv.get("libelle"):
                    ttk.Label(bloc, text=sv["libelle"], wraplength=460, foreground="#555").pack(anchor="w")
                if sv["source"] == "manuel":
                    ttk.Label(bloc, text="Note ajoutée à la main.", foreground="#777").pack(anchor="w")
                    continue
                if sv["source"] == "auto":
                    ttk.Label(bloc, text="Calculé automatiquement (lendemain de la dernière nuit).",
                              foreground="#777").pack(anchor="w")
                    continue
                chemin = trouver_bulletin(sv)
                if chemin:
                    ttk.Label(bloc, text="Lu dans : " + os.path.basename(chemin),
                              foreground="#1a5fb4").pack(anchor="w", pady=(4, 0))
                    if chemin not in deja:
                        ttk.Button(bloc, text="Ouvrir le PDF",
                                   command=lambda c=chemin: self.ouvrir_fichier(c)).pack(anchor="w", pady=(2, 0))
                        deja.add(chemin)
                else:
                    ttk.Label(bloc, text="Bulletin d'origine introuvable (déplacé ou supprimé ?).",
                              foreground="#a33").pack(anchor="w", pady=(4, 0))
                ttk.Separator(cadre).pack(fill="x", pady=4)

            boutons = ttk.Frame(cadre, padding=(0, 10, 0, 0))
            boutons.pack(fill="x")
            ttk.Button(boutons, text="Ajouter une note",
                       command=lambda: (fen.destroy(), self._ajouter_a(date))).pack(side="left")
            ttk.Button(boutons, text="Fermer", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())
            fen.grab_set()

        def choisir_theme_planning(self, nom):
            """Change le thème du planning (écran, et PDF si la case est cochée)."""
            pdf_aussi = bool(self.var_theme_pdf.get())
            ancien_pdf = theme_pdf()
            ecrire_pref("theme_planning", nom)
            ecrire_pref("theme_planning_pdf", pdf_aussi)
            regler_themes(nom, nom if pdf_aussi else THEME_DEFAUT)
            try:
                self.var_theme_pl.set(nom)
            except Exception:
                pass
            self.planifier_rendu()
            msg = f"Thème du planning : {nom}."
            if theme_pdf() != ancien_pdf:
                msg += " Cliquez sur « Mettre à jour le PDF » pour l'appliquer au PDF."
            self.statut.config(text=msg)

        def fenetre_themes(self):
            """Aperçu de tous les thèmes sur le mois affiché ; un clic applique le thème."""
            fen = tk.Toplevel(self)
            fen.title("Thèmes du planning")
            fen.transient(self)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text=f"Aperçu sur {MOIS_LONG[self.mois - 1]} {self.annee} — cliquez sur un thème",
                      font=("", 12, "bold")).pack(anchor="w", pady=(0, 10))
            grille = ttk.Frame(cadre)
            grille.pack()
            fen._images = []                           # garde les vignettes en mémoire
            boutons = {}

            def marquer():
                for n, b in boutons.items():
                    b.configure(text=("✓ " if n == _theme_courant["ecran"] else "") + n)

            def choisir(n):
                self.choisir_theme_planning(n)
                marquer()

            for k, nom in enumerate(THEMES_PLANNING):
                img, _ = dessiner_planning(self.annee, self.mois, self.pl.jours, 320, 226, self.pl.agent,
                                           self.pl.greves, theme=nom)
                photo = ImageTk.PhotoImage(img)
                fen._images.append(photo)
                b = ttk.Button(grille, image=photo, compound="top", command=lambda n=nom: choisir(n))
                b.grid(row=k // 4, column=k % 4, padx=5, pady=5)
                boutons[nom] = b
            marquer()
            bas = ttk.Frame(cadre)
            bas.pack(fill="x", pady=(10, 0))
            ttk.Checkbutton(bas, text="Utiliser aussi pour le PDF et l'impression", variable=self.var_theme_pdf,
                            command=lambda: choisir(_theme_courant["ecran"])).pack(side="left")
            ttk.Button(bas, text="Fermer", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())

        def montrer_changements(self, changements):
            """Liste des jours modifiés par une nouvelle commande."""
            fen = tk.Toplevel(self)
            fen.title("Changements dans les commandes")
            fen.transient(self)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            n = len(changements)
            ttk.Label(cadre, text=f"{n} jour{'s' if n > 1 else ''} modifié{'s' if n > 1 else ''} par la nouvelle commande",
                      font=("", 12, "bold")).pack(anchor="w", pady=(0, 8))
            zone = ttk.Frame(cadre)
            zone.pack(fill="both", expand=True)
            txt = tk.Text(zone, width=90, height=min(18, n + 1), wrap="word", font=(self.police_ui, 10),
                          relief="flat", padx=8, pady=6)
            barre = ttk.Scrollbar(zone, command=txt.yview)
            txt.configure(yscrollcommand=barre.set)
            barre.pack(side="right", fill="y")
            txt.pack(side="left", fill="both", expand=True)
            for d, avant, apres in sorted(changements):
                txt.insert("end", texte_changement(d, avant, apres) + "\n")
            txt.configure(state="disabled")
            boutons = ttk.Frame(cadre)
            boutons.pack(fill="x", pady=(10, 0))
            premier = min(d for d, _, _ in changements)

            def aller():
                self.annee, self.mois = premier.year, premier.month
                self.rendre()
                fen.destroy()

            ttk.Button(boutons, text="Voir le planning", command=aller).pack(side="left")
            ttk.Button(boutons, text="Fermer", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())

        def exporter_agenda(self):
            """Fichier .ics pour Outlook, Google Agenda ou l'agenda du téléphone."""
            fen = tk.Toplevel(self)
            fen.title("Exporter vers l'agenda")
            fen.transient(self)
            fen.resizable(False, False)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text="Un rendez-vous par service (de la prise à la fin de service) ;\n"
                      "les repos et codes sans horaires sont mis sur la journée entière.",
                      justify="left").pack(anchor="w", pady=(0, 8))
            portee = tk.StringVar(value="futur")
            ttk.Radiobutton(cadre, text="À partir d'aujourd'hui", value="futur", variable=portee).pack(anchor="w")
            ttk.Radiobutton(cadre, text="Tout le planning", value="tout", variable=portee).pack(anchor="w")

            def enregistrer():
                chemin = filedialog.asksaveasfilename(parent=fen, title="Enregistrer le calendrier",
                                                      defaultextension=".ics", initialfile="Planning.ics",
                                                      filetypes=[("Calendrier (iCalendar)", "*.ics")])
                if not chemin:
                    return
                depuis = dt.date.today() if portee.get() == "futur" else None
                try:
                    with open(chemin, "w", encoding="utf-8", newline="") as f:
                        f.write(texte_ics(self.pl.jours, self.pl.greves, depuis))
                except OSError as ex:
                    messagebox.showerror("Agenda", f"Enregistrement impossible :\n{ex}", parent=fen)
                    return
                fen.destroy()
                messagebox.showinfo("Agenda", "Calendrier enregistré :\n" + chemin + "\n\n"
                                    "Outlook : ouvrez le fichier. Google Agenda : Paramètres > Importer et exporter. "
                                    "Téléphone : envoyez-vous le fichier et ouvrez-le.")

            b = ttk.Frame(cadre)
            b.pack(fill="x", pady=(12, 0))
            ttk.Button(b, text="Enregistrer…", command=enregistrer).pack(side="left")
            ttk.Button(b, text="Annuler", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())

        def envoyer_telephone(self):
            """Copie du planning + codes + thème, à reprendre dans l'application mobile."""
            chemin = filedialog.asksaveasfilename(title="Envoyer vers le téléphone", defaultextension=".json",
                                                  initialfile="planning pour le téléphone.json",
                                                  filetypes=[("Copie du planning", "*.json")])
            if not chemin:
                return
            data = self.pl.vers_dict()
            try:
                with open(chemin_codes(), encoding="utf-8-sig") as f:
                    codes = f.read()
            except (OSError, UnicodeDecodeError):
                codes = ""
            data["mobile"] = {"codesTxt": codes, "theme": _theme_courant["pdf"]}
            try:
                with open(chemin, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=1)
            except OSError as ex:
                messagebox.showerror("Envoyer vers le téléphone", str(ex))
                return
            messagebox.showinfo("Envoyer vers le téléphone",
                                "Fichier enregistré :\n" + chemin + "\n\nCopiez-le sur le téléphone (mail, câble, "
                                "Drive…), puis dans l'application : Réglages > Reprendre une copie.")

        def reprendre_telephone(self):
            """Ajoute au planning une copie venant du téléphone (.json ou PDF créé par l'appli)."""
            chemin = filedialog.askopenfilename(title="Reprendre une copie du téléphone",
                                                filetypes=[("Copie du planning", "*.json *.pdf"),
                                                           ("Tous les fichiers", "*.*")])
            if not chemin:
                return
            try:
                if chemin.lower().endswith(".pdf"):
                    autre = lire_planning_exporte(chemin)
                    if autre is None:
                        raise ValueError("ce PDF ne contient pas de planning.")
                else:
                    with open(chemin, encoding="utf-8") as f:
                        autre = Planning.depuis_dict(json.load(f))
            except Exception as ex:
                messagebox.showerror("Reprendre une copie", f"Fichier non reconnu :\n{ex}")
                return
            creer_sauvegarde(self.pl, "avant copie du téléphone")
            n = self.pl.fusionner(autre)
            self.tout_rafraichir()
            messagebox.showinfo("Reprendre une copie", f"{n} jour(s) repris du téléphone (le plus récent l'emporte).\n"
                                "Les codes et distances du PC ne sont pas modifiés.")

        def choisir_impression(self):
            """Fenêtre : choisir les mois à imprimer."""
            fen = tk.Toplevel(self)
            fen.title("Imprimer le planning")
            fen.transient(self)
            fen.resizable(False, False)
            cadre = ttk.Frame(fen, padding=14)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text="Mois à imprimer :", font=("", 12, "bold")).pack(anchor="w", pady=(0, 6))

            mois = sorted({(d.year, d.month) for d in self.pl.jours} | {(self.annee, self.mois)}, reverse=True)
            # liste défilante (il peut y avoir beaucoup de mois)
            zone = ttk.Frame(cadre)
            zone.pack(fill="both", expand=True)
            toile = tk.Canvas(zone, width=260, height=min(320, 26 * len(mois) + 4), highlightthickness=0)
            barre_def = ttk.Scrollbar(zone, orient="vertical", command=toile.yview)
            interieur = ttk.Frame(toile)
            interieur.bind("<Configure>", lambda e: toile.configure(scrollregion=toile.bbox("all")))
            toile.create_window((0, 0), window=interieur, anchor="nw")
            toile.configure(yscrollcommand=barre_def.set)
            toile.pack(side="left", fill="both", expand=True)
            if len(mois) > 12:
                barre_def.pack(side="right", fill="y")
            choix = {}
            for a, m in mois:
                v = tk.BooleanVar(value=(a, m) == (self.annee, self.mois))
                choix[(a, m)] = v
                ttk.Checkbutton(interieur, text=f"{MOIS_LONG[m - 1].capitalize()} {a}", variable=v).pack(anchor="w")

            rapide = ttk.Frame(cadre, padding=(0, 6))
            rapide.pack(fill="x")
            ttk.Button(rapide, text="Tout", width=8,
                       command=lambda: [v.set(True) for v in choix.values()]).pack(side="left")
            ttk.Button(rapide, text="Aucun", width=8,
                       command=lambda: [v.set(False) for v in choix.values()]).pack(side="left", padx=4)

            def lancer(apercu):
                selection = [k for k, v in choix.items() if v.get()]
                if not selection:
                    messagebox.showinfo("Imprimer", "Cochez au moins un mois.", parent=fen)
                    return
                self.config(cursor="watch")
                fen.config(cursor="watch")
                self.update_idletasks()
                try:
                    chemin = pdf_impression(self.pl, selection)
                except Exception as ex:
                    messagebox.showerror("Imprimer", f"Impossible de préparer l'impression :\n{ex}", parent=fen)
                    return
                finally:
                    self.config(cursor="")
                    fen.config(cursor="")
                fen.destroy()
                if apercu:
                    self.ouvrir_fichier(chemin)
                    self.statut.config(text=f"Aperçu de {len(selection)} mois ouvert : imprimez avec Ctrl+P.")
                elif imprimer_fichier(chemin):
                    self.statut.config(text=f"{len(selection)} mois envoyé(s) à l'imprimante par défaut.")
                else:
                    # pas de lecteur PDF capable d'imprimer directement : on ouvre le PDF
                    self.ouvrir_fichier(chemin)
                    messagebox.showinfo("Imprimer", "L'impression directe n'est pas possible avec votre lecteur PDF.\n"
                                                    "Le planning est ouvert : imprimez-le avec Ctrl+P.")

            boutons = ttk.Frame(cadre, padding=(0, 8, 0, 0))
            boutons.pack(fill="x")
            ttk.Button(boutons, text="Imprimer", command=lambda: lancer(False)).pack(side="left")
            ttk.Button(boutons, text="Aperçu", command=lambda: lancer(True)).pack(side="left", padx=4)
            ttk.Button(boutons, text="Annuler", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())
            fen.grab_set()

        def afficher_sortie(self):
            etat = "normal" if self.pl.sortie else "disabled"
            try:
                self.menu_fichier.entryconfig(self._idx_maj_pdf, state=etat)
                self.btn_maj.state(["!disabled"] if self.pl.sortie else ["disabled"])
            except Exception:
                pass
            self.title(f"Planning PDF — {os.path.basename(self.pl.sortie)}" if self.pl.sortie
                       else "Planning PDF")

        # ---- menus ---------------------------------------------------------
        def creer_menus(self):
            barre = tk.Menu(self)
            ctrl = "Cmd" if sys.platform == "darwin" else "Ctrl"
            mod = "Command" if sys.platform == "darwin" else "Control"

            fichier = tk.Menu(barre, tearoff=False)
            fichier.add_command(label="Ajouter une commande…", accelerator=f"{ctrl}+O", command=self.ouvrir)
            fichier.add_command(label="Dossier commande", accelerator=f"{ctrl}+D", command=self.dossier_commande)
            if MAIL_DISPONIBLE:                                         # version « mail » seulement
                fichier.add_command(label="Commandes par mail…", command=self.fenetre_mail)
            fichier.add_separator()
            fichier.add_command(label="Exporter…", accelerator=f"{ctrl}+E", command=self.exporter)
            fichier.add_command(label="Exporter vers l'agenda (.ics)…", command=self.exporter_agenda)
            fichier.add_separator()
            fichier.add_command(label="Envoyer vers le téléphone (.json)…", command=self.envoyer_telephone)
            fichier.add_command(label="Reprendre une copie du téléphone…", command=self.reprendre_telephone)
            fichier.add_command(label="Mettre à jour le PDF", accelerator=f"{ctrl}+S", command=self.maj_sortie)
            self._idx_maj_pdf = fichier.index("end")
            fichier.add_command(label="Imprimer…", accelerator=f"{ctrl}+P", command=self.choisir_impression)
            fichier.add_separator()
            fichier.add_command(label="Sauvegardes…", command=self.fenetre_sauvegardes)
            fichier.add_command(label="Nouveau planning (zone de danger)…", command=self.nouveau)
            fichier.add_separator()
            fichier.add_command(label="Quitter", accelerator=f"{ctrl}+Q", command=self.quitter)
            barre.add_cascade(label="Fichier", menu=fichier)
            self.menu_fichier = fichier

            edition = tk.Menu(barre, tearoff=False)
            edition.add_command(label="Ajouter une note…", accelerator=f"{ctrl}+N", command=self.ajouter)
            edition.add_command(label="Modifier la ligne sélectionnée…", command=self.modifier)
            edition.add_command(label="Supprimer la ligne sélectionnée", accelerator="Suppr", command=self.supprimer)
            edition.add_separator()
            edition.add_command(label="Codes et couleurs…", accelerator=f"{ctrl}+K", command=self.editeur_codes)
            edition.add_command(label="Éditer codes.txt en texte…", command=self.ouvrir_codes)
            barre.add_cascade(label="Édition", menu=edition)

            affichage = tk.Menu(barre, tearoff=False)
            affichage.add_command(label="Mois précédent", accelerator="Page préc.", command=lambda: self.decaler(-1))
            affichage.add_command(label="Mois suivant", accelerator="Page suiv.", command=lambda: self.decaler(1))
            affichage.add_command(label="Mois en cours", accelerator="Début", command=self.mois_courant)
            affichage.add_separator()
            affichage.add_command(label="Basculer thème clair / sombre", command=self.basculer_theme)
            themes = tk.Menu(affichage, tearoff=False)
            self.var_theme_pl = tk.StringVar(value=_theme_courant["ecran"])
            for nom in THEMES_PLANNING:
                themes.add_radiobutton(label=nom, value=nom, variable=self.var_theme_pl,
                                       command=lambda n=nom: self.choisir_theme_planning(n))
            themes.add_separator()
            self.var_theme_pdf = tk.BooleanVar(value=bool(lire_prefs().get("theme_planning_pdf", True)))
            themes.add_checkbutton(label="Utiliser aussi pour le PDF et l'impression",
                                   variable=self.var_theme_pdf,
                                   command=lambda: self.choisir_theme_planning(_theme_courant["ecran"]))
            themes.add_command(label="Aperçu des thèmes…", command=self.fenetre_themes)
            affichage.add_cascade(label="Thème du planning", menu=themes)
            self.var_infos = tk.BooleanVar(value=bool(lire_prefs().get("infos_extraites", False)))
            affichage.add_checkbutton(label="Informations extraites", accelerator=f"{ctrl}+L",
                                      variable=self.var_infos, command=self.appliquer_panneau)
            barre.add_cascade(label="Affichage", menu=affichage)

            aide = tk.Menu(barre, tearoff=False)
            aide.add_command(label="Lisez-moi",
                             command=lambda: self.ouvrir_fichier(os.path.join(APP_DIR, "LISEZMOI.txt")))
            aide.add_command(label="Notice des codes",
                             command=lambda: self.ouvrir_fichier(os.path.join(APP_DIR, "NOTICE_CODES.txt")))
            aide.add_command(label="Rechercher une mise à jour…", command=lambda: self.verifier_maj(manuel=True))
            aide.add_separator()
            aide.add_command(label="À propos", command=lambda: messagebox.showinfo(
                "À propos", f"Planning PDF {version_appli()}"
                + (f" (fabrication {version_build()[0]})" if version_build()[0] else "")
                + ("\nVersion mail : commandes récupérées dans la boîte mail." if MAIL_DISPONIBLE else "\nVersion manuelle (sans mail).")
                + "\n\nTransforme les bulletins de commande (PDF) en planning mensuel."
                + "\nHistorique des versions : CHANGELOG.md (dépôt GitHub)."))
            barre.add_cascade(label="Aide", menu=aide)
            self.config(menu=barre)

            # raccourcis clavier
            for touche, action in (("o", self.ouvrir), ("d", self.dossier_commande), ("e", self.exporter),
                                   ("s", self.maj_sortie), ("p", self.choisir_impression),
                                   ("q", self.quitter), ("n", self.ajouter), ("k", self.editeur_codes)):
                self.bind(f"<{mod}-{touche}>", lambda e, a=action: a())
            self.bind(f"<{mod}-l>", lambda e: (self.var_infos.set(not self.var_infos.get()),
                                                self.appliquer_panneau()))
            self.bind("<Prior>", lambda e: self.decaler(-1))
            self.bind("<Next>", lambda e: self.decaler(1))
            self.bind("<Home>", lambda e: self.mois_courant())

        # ---- apparence ---------------------------------------------------
        def appliquer_icone(self):
            """Icône du programme sur toutes les fenêtres (à la place de la plume de Tk)."""
            try:
                self._icones = [tk.PhotoImage(data=ICONE_64), tk.PhotoImage(data=ICONE_32)]
                self.iconphoto(True, *self._icones)
            except Exception:
                pass

        def appliquer_theme(self):
            """Thème Windows 11 (Sun Valley) si disponible ; clair ou sombre (choix mémorisé)."""
            charger_themes()                         # thème du planning (écran / PDF)
            self.police_ui = "Segoe UI" if sys.platform.startswith("win") else "Helvetica"
            try:
                import tkinter.font as tkfont
                for nom in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont"):
                    tkfont.nametofont(nom).configure(family=self.police_ui, size=10)
            except Exception:
                pass
            self.sombre = bool(lire_prefs().get("theme_sombre", False))
            self.theme_moderne = False
            try:
                import sv_ttk
                sv_ttk.set_theme("dark" if self.sombre else "light")
                self.theme_moderne = True
            except Exception:
                pass                                 # thème absent : apparence Windows classique
            self.fond_canvas = "#202020" if (self.sombre and self.theme_moderne) else "#eceff3"
            self.accent = "#0a64d6"
            self.couleur_texte = "#f2f2f2" if (self.sombre and self.theme_moderne) else "#1f1f1f"

        def basculer_theme(self):
            ecrire_pref("theme_sombre", not self.sombre)
            if not self.theme_moderne:
                messagebox.showinfo("Thème", "Le thème moderne n'est pas installé : refaites l'exe avec "
                                    "« Creer l'exe (Windows).bat ».")
                return
            try:
                import sv_ttk
                self.sombre = not self.sombre
                sv_ttk.set_theme("dark" if self.sombre else "light")
            except Exception:
                return
            self.fond_canvas = "#202020" if self.sombre else "#eceff3"
            self.couleur_texte = "#f2f2f2" if self.sombre else "#1f1f1f"
            self.canvas.configure(bg=self.fond_canvas)
            self.resume.configure(bg=self.fond_resume())
            self.planifier_rendu()

        def fond_resume(self):
            try:
                return ttk.Style().lookup("TFrame", "background") or self.cget("bg")
            except Exception:
                return "#f3f3f3"

        def maj_resume(self):
            """Pastilles : nombre de jours par code sur le mois affiché, grèves."""
            for w in self.resume.winfo_children():
                w.destroy()
            fond = self.fond_resume()
            self.resume.configure(bg=fond)
            jours = pour_affichage(self.pl.jours)
            compte, couleurs = Counter(), {}
            for d, services in jours.items():
                if (d.year, d.month) != (self.annee, self.mois) or not services:
                    continue
                sv = next((x for x in services if x.get("code")), None)
                if not sv:
                    continue
                nom = nom_affiche(sv)
                compte[nom] += 1
                info = infos_code(sv["code"])
                if info and info["fond"]:
                    couleurs[nom] = info["fond"]
            if not compte:
                tk.Label(self.resume, text="Aucune information pour ce mois.", bg=fond,
                         fg="#888888", font=(self.police_ui, 10)).pack(side="left")
                return
            tk.Label(self.resume, text="Ce mois :", bg=fond, fg=self.couleur_texte,
                     font=(self.police_ui, 10, "bold")).pack(side="left", padx=(0, 6))
            for nom, n in compte.most_common(10):
                rvb = couleurs.get(nom, (225, 228, 233))
                sombre = (0.299 * rvb[0] + 0.587 * rvb[1] + 0.114 * rvb[2]) < 140
                tk.Label(self.resume, text=f" {nom}  {n} ", bg="#%02x%02x%02x" % tuple(rvb),
                         fg="white" if sombre else "#1f1f1f", font=(self.police_ui, 9, "bold"),
                         padx=6, pady=2).pack(side="left", padx=2)
            nb_greve = sum(1 for d in self.pl.greves if (d.year, d.month) == (self.annee, self.mois))
            if nb_greve:
                tk.Label(self.resume, text=f" GRÈVE  {nb_greve} ", bg="#c00000", fg="white",
                         font=(self.police_ui, 9, "bold"), padx=6, pady=2).pack(side="left", padx=(8, 2))
            r = recap_mois(self.pl.jours, self.annee, self.mois, self.pl.greves)
            if r["travailles"]:
                morceaux = [f"{heures_txt(r['minutes'])} de service", f"{r['nuits']} nuit{'s' if r['nuits'] > 1 else ''}",
                            f"{r['dimanches']} dim.", f"{r['feries']} férié{'s' if r['feries'] > 1 else ''}",
                            f"{r['repos']} repos"]
                tk.Label(self.resume, text="   ·   ".join(morceaux) + "      ", bg=fond, fg=self.couleur_texte,
                         font=(self.police_ui, 10)).pack(side="right")

        # ---- planning interactif ------------------------------------------
        def survol(self, e):
            date = self.case_sous(e)
            if date == self._survol:
                return
            self._survol = date
            self.canvas.delete("survol")
            self.masquer_bulle()
            if date is None:
                self.canvas.configure(cursor="")
                return
            self.canvas.configure(cursor="hand2")
            a, b, c, d_ = self.cases[date]
            self.canvas.create_rectangle(a + 1, b + 1, c - 1, d_ - 1, outline="#f0a000", width=3, tags="survol")
            self._bulle_minuteur = self.after(600, lambda: self.afficher_bulle(date, e.x_root, e.y_root))

        def molette(self, e):
            """Molette au-dessus du planning : mois précédent / suivant."""
            try:
                sous = self.winfo_containing(e.x_root, e.y_root)
            except Exception:
                sous = None
            if sous is self.canvas:
                self.masquer_bulle()
                self.decaler(-1 if e.delta > 0 else 1)

        def fin_survol(self):
            self._survol = None
            self.canvas.delete("survol")
            self.canvas.configure(cursor="")
            self.masquer_bulle()

        def afficher_bulle(self, date, x, y):
            self._bulle_minuteur = None
            if date != self._survol:
                return
            lignes = [f"{JOURS[date.weekday()].capitalize()} {date.day} {MOIS_LONG[date.month - 1]} {date.year}"]
            if date in self.pl.greves:
                lignes.append("⚑ Jour de grève")
            for sv in avec_dn(self.pl.jours).get(date, []):
                titre = nom_affiche(sv) if sv.get("code") else "Note"
                lignes.append("• " + titre + (f"  ({sv['code']})" if sv.get("code") and titre != sv["code"] else ""))
                lignes += ["     " + h for h in lignes_horaires(sv.get("horaires", ""))]
                if sv.get("source") == "manuel" and sv.get("libelle"):
                    lignes.append("     " + sv["libelle"])
            if len(lignes) == 1:
                lignes.append("Aucune information")
            lignes.append("Clic : détail   ·   clic droit : grève, note")
            self._bulle = tk.Toplevel(self)
            self._bulle.wm_overrideredirect(True)
            self._bulle.wm_geometry(f"+{x + 16}+{y + 16}")
            tk.Label(self._bulle, text="\n".join(lignes), justify="left", bg="#fffbe6", fg="#1f1f1f",
                     relief="solid", bd=1, padx=8, pady=6, font=(self.police_ui, 9)).pack()

        def masquer_bulle(self):
            if self._bulle_minuteur:
                self.after_cancel(self._bulle_minuteur)
                self._bulle_minuteur = None
            if self._bulle is not None:
                try:
                    self._bulle.destroy()
                except Exception:
                    pass
                self._bulle = None

        def appliquer_panneau(self):
            """Affiche ou masque le cadre « Informations extraites » (choix mémorisé)."""
            visible = bool(self.var_infos.get())
            deja = str(self.panneau_infos) in [str(p_) for p_ in self.corps.panes()]
            if visible and not deja:
                self.corps.add(self.panneau_infos, weight=1)
            elif not visible and deja:
                self.corps.forget(self.panneau_infos)
            ecrire_pref("infos_extraites", visible)
            self.planifier_rendu()

        def mois_courant(self):
            auj = dt.date.today()
            self.annee, self.mois = auj.year, auj.month
            self.planifier_rendu()

        def _ecrire_pdf(self, chemin):
            self.config(cursor="watch")
            self.update_idletasks()
            try:
                exporter_pdf(chemin, self.pl, en_plus=(self.annee, self.mois) if not self.pl.jours else None)
                return True
            except Exception as ex:
                messagebox.showerror("Erreur", f"Impossible d'écrire le PDF :\n{ex}")
                return False
            finally:
                self.config(cursor="")

        def maj_sortie(self, msg_avant=""):
            """Réécrit le PDF de sortie avec tout le planning (une page par mois)."""
            if not self.pl.sortie:
                return
            if not os.path.isdir(os.path.dirname(self.pl.sortie) or "."):
                messagebox.showwarning("PDF introuvable", "Le dossier du PDF de sortie n'existe plus.\n"
                                       "Utilisez « Exporter… » pour choisir un nouvel emplacement.")
                return
            if self._ecrire_pdf(self.pl.sortie):
                self.modifie = False
                nb = len(mois_du_planning(self.pl))
                self.statut.config(text=f"{msg_avant}PDF mis à jour ({nb} mois) : {self.pl.sortie}")

        def exporter(self):
            chemin = filedialog.asksaveasfilename(
                title="Exporter le planning", initialdir=self.dernier_dossier,
                initialfile=(os.path.basename(self.pl.sortie) if self.pl.sortie else "planning"),
                defaultextension=".pdf",
                filetypes=[("PDF (tous les mois, mis à jour automatiquement)", "*.pdf"),
                           ("Image PNG (mois affiché)", "*.png"), ("Image JPEG (mois affiché)", "*.jpg")])
            if not chemin:
                return
            self.dernier_dossier = os.path.dirname(chemin)
            if chemin.lower().endswith(".pdf"):
                if self._ecrire_pdf(chemin):
                    self.pl.sortie = chemin
                    self.modifie = False
                    try:
                        self.pl.sauver(self.fichier_etat)
                    except OSError:
                        pass
                    self.afficher_sortie()
                    self.statut.config(text=f"PDF créé : {chemin} — il sera complété automatiquement "
                                            "à chaque nouveau bulletin ouvert.")
                return
            try:
                exporter_image(chemin, self.annee, self.mois, self.pl.jours, self.pl.agent, self.pl.greves)
            except Exception as ex:
                messagebox.showerror("Erreur", f"Export impossible :\n{ex}")
                return
            self.statut.config(text=f"Image exportée : {chemin}")

        def editeur_codes(self):
            """Éditeur graphique de codes.txt."""
            from tkinter import colorchooser
            lignes = [list(x) for x in lire_codes_fichier()]     # [code, nom, fond, gros]
            codes_bulletins = sorted({sv["code"] for v in self.pl.jours.values() for sv in v
                                      if sv.get("code") and sv.get("source") == "pdf"})

            fen = tk.Toplevel(self)
            fen.title("Codes du planning")
            fen.transient(self)
            cadre = ttk.Frame(fen, padding=12)
            cadre.pack(fill="both", expand=True)
            ttk.Label(cadre, text="Codes du planning", font=("", 12, "bold")).pack(anchor="w")
            ttk.Label(cadre, foreground="#666", text="Double-clic sur une ligne pour la modifier. "
                      "Les changements s'appliquent après « Enregistrer ».").pack(anchor="w", pady=(0, 6))

            zone = ttk.Frame(cadre)
            zone.pack(fill="both", expand=True)
            arbre = ttk.Treeview(zone, columns=("code", "nom", "couleur", "gros"), show="headings", height=16)
            for c, t, w in (("code", "Code du bulletin", 130), ("nom", "Nom affiché", 200),
                            ("couleur", "Couleur", 110), ("gros", "Gros", 60)):
                arbre.heading(c, text=t)
                arbre.column(c, width=w, anchor="center" if c == "gros" else "w")
            sb = ttk.Scrollbar(zone, orient="vertical", command=arbre.yview)
            arbre.configure(yscrollcommand=sb.set)
            arbre.pack(side="left", fill="both", expand=True)
            sb.pack(side="right", fill="y")
            etat = {"modifie": False}

            def hexa(fond):
                return "#%02x%02x%02x" % tuple(fond) if fond else ""

            def remplir(selection=None):
                arbre.delete(*arbre.get_children())
                for k, (code, nom, fond, gros) in enumerate(lignes):
                    tag = f"t{k}"
                    arbre.insert("", "end", iid=str(k), tags=(tag,),
                                 values=(code, nom, (nom_couleur(fond) or "—")
                                         + (" (fixe)" if fond_bloque(code) else ""), "oui" if gros else ""))
                    if fond:
                        sombre = (0.299 * fond[0] + 0.587 * fond[1] + 0.114 * fond[2]) < 140
                        arbre.tag_configure(tag, background=hexa(fond),
                                            foreground="white" if sombre else "black")
                if selection is not None and 0 <= selection < len(lignes):
                    arbre.selection_set(str(selection))
                    arbre.see(str(selection))

            def fiche(k=None):
                """Fenêtre d'édition d'une ligne (k = index, None = nouvelle ligne)."""
                code, nom, fond, gros = lignes[k] if k is not None else ["", "", None, False]
                f2 = tk.Toplevel(fen)
                f2.title("Modifier le code" if k is not None else "Ajouter un code")
                f2.transient(fen)
                f2.resizable(False, False)
                c2 = ttk.Frame(f2, padding=14)
                c2.pack(fill="both", expand=True)
                v_code, v_nom = tk.StringVar(value=code), tk.StringVar(value=nom)
                v_coul, v_gros = tk.StringVar(value=nom_couleur(fond)), tk.BooleanVar(value=gros)
                choix_fond = {"rvb": fond}

                ttk.Label(c2, text="Code du bulletin (colonne « Utilisation ») :").grid(row=0, column=0, sticky="w")
                ttk.Combobox(c2, textvariable=v_code, values=codes_bulletins, width=28).grid(
                    row=1, column=0, columnspan=3, sticky="w", pady=(0, 8))
                ttk.Label(c2, text="Nom affiché dans le planning :").grid(row=2, column=0, sticky="w")
                ttk.Entry(c2, textvariable=v_nom, width=31).grid(row=3, column=0, columnspan=3, sticky="w")
                ttk.Label(c2, foreground="#666", justify="left", text=(
                    "[date] = intitulé écrit sous la date dans le bulletin,\n"
                    "ex. « [date] travaux ». Vide = même nom que le code.")).grid(
                    row=4, column=0, columnspan=3, sticky="w", pady=(0, 8))
                ttk.Label(c2, text="Couleur de fond (cliquez sur une pastille) :").grid(
                    row=5, column=0, columnspan=3, sticky="w")
                bloc_coul = ttk.Frame(c2)
                bloc_coul.grid(row=6, column=0, columnspan=3, sticky="w", pady=(2, 0))
                grille = tk.Frame(bloc_coul, bg="#bbbbbb", bd=0)
                grille.pack(side="left")
                pastilles = {}

                def choisir(rvb):
                    if fond_bloque(v_code.get()):            # couleur imposée : pas de choix
                        return
                    v_coul.set("" if rvb is None else "#%02X%02X%02X" % tuple(rvb))

                for r_, rangee in enumerate(palette_couleurs()):
                    for c_, rvb in enumerate(rangee):
                        p_ = tk.Frame(grille, width=22, height=22, bg=hexa(rvb), cursor="hand2",
                                      highlightthickness=2, highlightbackground="#bbbbbb")
                        p_.grid(row=r_, column=c_, padx=1, pady=1)
                        p_.bind("<Button-1>", lambda e, x=rvb: choisir(x))
                        pastilles[tuple(rvb)] = p_

                cote = ttk.Frame(bloc_coul, padding=(10, 0, 0, 0))
                cote.pack(side="left", fill="y")
                apercu = tk.Label(cote, text="Aperçu", width=16, height=3, relief="solid", bd=1)
                apercu.pack(anchor="w")
                valeur = ttk.Label(cote, foreground="#555")
                valeur.pack(anchor="w", pady=(4, 6))

                def autre_couleur():
                    rvb, _ = colorchooser.askcolor(color=hexa(choix_fond["rvb"]) if choix_fond["rvb"]
                                                   not in (None, "erreur") else "#ffffff",
                                                   parent=f2, title="Choisir une couleur")
                    if rvb:
                        choisir(tuple(int(x) for x in rvb))

                b_aucune = ttk.Button(cote, text="Aucune", command=lambda: choisir(None))
                b_aucune.pack(anchor="w", fill="x")
                b_autre = ttk.Button(cote, text="Autre…", command=autre_couleur)
                b_autre.pack(anchor="w", fill="x", pady=(4, 0))
                note_fixe = ttk.Label(c2, foreground="#8a5a00", text=(
                    "Couleur fixe : les repos, congés et fêtes (C, AH, RP, RPP, F, F0…F9…)\n"
                    "sont toujours en orange."))

                def verrou(*_):
                    """Code à couleur imposée : orange, palette et boutons désactivés."""
                    bloque = fond_bloque(v_code.get())
                    if bloque and v_coul.get() != nom_couleur(FOND_BLOQUE):
                        v_coul.set(nom_couleur(FOND_BLOQUE))
                    for b_ in (b_aucune, b_autre):
                        b_.state(["disabled"] if bloque else ["!disabled"])
                    for p_ in pastilles.values():
                        p_.config(cursor="arrow" if bloque else "hand2")
                    if bloque:
                        note_fixe.grid(row=7, column=0, columnspan=3, sticky="w", pady=(6, 0))
                    else:
                        note_fixe.grid_remove()

                ttk.Checkbutton(c2, text="Écrire en très gros au centre de la case (comme RP)",
                                variable=v_gros).grid(row=8, column=0, columnspan=3, sticky="w", pady=(10, 0))

                def maj_apercu(*_):
                    t = v_coul.get().strip()
                    try:
                        rvb = None if t in ("", "(aucune)") else _couleur(t)
                    except ValueError:
                        apercu.config(text="couleur ?", bg="white", fg="#b00020")
                        choix_fond["rvb"] = "erreur"
                        return
                    choix_fond["rvb"] = rvb
                    for cle, p_ in pastilles.items():          # pastille choisie entourée
                        p_.config(highlightbackground="#000000" if rvb and cle == tuple(rvb) else "#bbbbbb")
                    valeur.config(text=(nom_couleur(rvb) or "aucune couleur") if rvb else "aucune couleur")
                    sombre = rvb and (0.299 * rvb[0] + 0.587 * rvb[1] + 0.114 * rvb[2]) < 140
                    apercu.config(text=(v_nom.get() or v_code.get() or "Aperçu")[:18],
                                  bg=hexa(rvb) or "white", fg="white" if sombre else "black",
                                  font=("", 11 if v_gros.get() else 9, "bold"))

                for v in (v_coul, v_nom, v_code, v_gros):
                    v.trace_add("write", maj_apercu)
                v_code.trace_add("write", verrou)
                verrou()
                maj_apercu()

                def valider():
                    c = v_code.get().strip().upper()
                    if not c or ";" in c:
                        messagebox.showwarning("Code", "Indiquez un code (sans « ; »).", parent=f2)
                        return
                    if choix_fond["rvb"] == "erreur":
                        messagebox.showwarning("Couleur", "Couleur non reconnue.", parent=f2)
                        return
                    if any(l[0] == c for j, l in enumerate(lignes) if j != k):
                        messagebox.showwarning("Code", f"Le code {c} existe déjà dans la liste.", parent=f2)
                        return
                    n = v_nom.get().strip().replace(";", ",") or c
                    ligne = [c, n, FOND_BLOQUE if fond_bloque(c) else choix_fond["rvb"], v_gros.get()]
                    if k is None:
                        lignes.append(ligne)
                        pos = len(lignes) - 1
                    else:
                        lignes[k] = ligne
                        pos = k
                    etat["modifie"] = True
                    f2.destroy()
                    remplir(pos)

                b2 = ttk.Frame(c2, padding=(0, 12, 0, 0))
                b2.grid(row=9, column=0, columnspan=3, sticky="we")
                ttk.Button(b2, text="OK", command=valider).pack(side="left")
                ttk.Button(b2, text="Annuler", command=f2.destroy).pack(side="right")
                f2.bind("<Return>", lambda e: valider())
                f2.bind("<Escape>", lambda e: f2.destroy())
                f2.grab_set()

            def selection_index():
                sel = arbre.selection()
                return int(sel[0]) if sel else None

            def modifier_sel(_e=None):
                k = selection_index()
                if k is not None:
                    fiche(k)

            def supprimer_sel():
                k = selection_index()
                if k is None:
                    return
                if messagebox.askyesno("Supprimer", f"Supprimer le code {lignes[k][0]} ?", parent=fen):
                    del lignes[k]
                    etat["modifie"] = True
                    remplir(min(k, len(lignes) - 1))

            def enregistrer():
                try:
                    ecrire_codes_fichier(lignes)
                except OSError as ex:
                    messagebox.showerror("Codes", f"Enregistrement impossible :\n{ex}", parent=fen)
                    return
                etat["modifie"] = False
                fen.destroy()
                self.verifier_codes()            # relit codes.txt et redessine le planning
                self.statut.config(text="Codes enregistrés. Cliquez sur « Mettre à jour le PDF » pour "
                                        "les reporter dans le PDF de sortie.")

            def fermer():
                if etat["modifie"] and not messagebox.askyesno(
                        "Codes", "Fermer sans enregistrer les modifications ?", parent=fen):
                    return
                fen.destroy()

            def texte():
                if etat["modifie"]:
                    messagebox.showinfo("Codes", "Enregistrez d'abord vos modifications.", parent=fen)
                    return
                fen.destroy()
                self.ouvrir_codes()

            boutons = ttk.Frame(cadre, padding=(0, 8, 0, 0))
            boutons.pack(fill="x")
            ttk.Button(boutons, text="Ajouter…", command=lambda: fiche(None)).pack(side="left")
            ttk.Button(boutons, text="Modifier…", command=modifier_sel).pack(side="left", padx=4)
            ttk.Button(boutons, text="Supprimer", command=supprimer_sel).pack(side="left")
            ttk.Button(boutons, text="Éditer en texte…", command=texte).pack(side="left", padx=(16, 0))
            ttk.Button(boutons, text="Enregistrer", command=enregistrer).pack(side="right")
            ttk.Button(boutons, text="Fermer", command=fermer).pack(side="right", padx=4)
            arbre.bind("<Double-1>", modifier_sel)
            arbre.bind("<Delete>", lambda e: supprimer_sel())
            fen.protocol("WM_DELETE_WINDOW", fermer)
            fen.bind("<Escape>", lambda e: fermer())
            remplir()
            fen.grab_set()

        def ouvrir_codes(self):
            """Ouvre codes.txt dans l'éditeur de texte du système."""
            codes_a_jour()                       # crée le fichier s'il n'existe pas
            chemin = chemin_codes()
            try:
                if sys.platform.startswith("win"):
                    # éditeur de texte par défaut ; le Bloc-notes seulement en dernier recours
                    ouvert = False
                    for essai in (lambda: os.startfile(chemin, "edit"), lambda: os.startfile(chemin),
                                  lambda: __import__("subprocess").Popen(["notepad.exe", chemin])):
                        try:
                            essai()
                            ouvert = True
                            break
                        except OSError:
                            continue
                    if not ouvert:
                        raise OSError("aucun éditeur de texte trouvé")
                elif sys.platform == "darwin":
                    import subprocess
                    subprocess.Popen(["open", "-t", chemin])
                else:
                    import subprocess
                    subprocess.Popen(["xdg-open", chemin])
            except Exception as ex:
                messagebox.showerror("Codes", f"Impossible d'ouvrir {chemin} :\n{ex}")
                return
            self.statut.config(text="Modifiez codes.txt puis enregistrez : le planning se mettra "
                                    "à jour en revenant dans cette fenêtre.")

        def ranger(self, silencieux=False):
            """Renomme les bulletins du dossier « commande » d'après leur période."""
            try:
                renommes, inconnus, erreurs = ranger_commandes()
            except Exception as ex:
                if not silencieux:
                    messagebox.showerror("Dossier commande", str(ex))
                return
            if renommes:
                self.statut.config(text=f"Dossier commande : {len(renommes)} bulletin(s) rangé(s) — "
                                        + ", ".join(os.path.basename(n) for _, n in renommes[:3])
                                        + ("…" if len(renommes) > 3 else ""))
            elif not silencieux:
                self.statut.config(text="Dossier commande : tout est déjà rangé.")
            bloques = [e for e in erreurs if "OUVERT" in e]
            if bloques:                              # toujours signalé, même en silencieux
                self.statut.config(text=f"⚠ {len(bloques)} bulletin(s) non rangé(s) car ouvert(s) dans un autre "
                                        "programme : fermez-le(s) puis cliquez sur « Dossier commande ».")
            if (inconnus or erreurs) and not silencieux:
                msg = []
                if inconnus:
                    msg.append("Non reconnus comme bulletins (laissés tels quels) :\n  • "
                               + "\n  • ".join(inconnus))
                if erreurs:
                    msg.append("Problèmes :\n  • " + "\n  • ".join(erreurs))
                messagebox.showwarning("Dossier commande", "\n\n".join(msg))

        def actualiser(self, silencieux=False):
            """Range le dossier « commande » puis ajoute au planning les bulletins pas encore lus."""
            if self._occupe:
                return
            self._occupe = True
            try:
                self.ranger(silencieux=silencieux)
                self.config(cursor="watch")
                self.update_idletasks()
                changements = []
                n, maj, ign, erreurs = synchroniser_dossier(self.pl, changements=changements)
                self.config(cursor="")
                if n:
                    self.tout_rafraichir()
                    msg = f"Dossier commande : {n} nouveau(x) bulletin(s) ajouté(s) au planning, {maj} jour(s) mis à jour."
                    if ign:
                        msg += f" {ign} jour(s) ignoré(s) (un bulletin plus récent existe)."
                    self.statut.config(text=msg)
                    if changements:
                        self.montrer_changements(changements)
                    if self.pl.sortie:
                        self.maj_sortie(msg_avant=msg + " ")
                else:
                    try:
                        self.pl.sauver(self.fichier_etat)     # mémorise les fichiers déjà vus
                    except OSError:
                        pass
                if erreurs and not silencieux:
                    messagebox.showwarning("Dossier commande", "Bulletins illisibles :\n  • " + "\n  • ".join(erreurs))
            except Exception as ex:
                self.config(cursor="")
                if not silencieux:
                    messagebox.showerror("Dossier commande", str(ex))
            finally:
                self._occupe = False

        def dossier_commande(self):
            """Range et lit les bulletins, puis ouvre le dossier dans l'explorateur."""
            self.actualiser()
            d = dossier_commandes()
            try:
                import subprocess
                if sys.platform.startswith("win"):
                    os.startfile(d)
                elif sys.platform == "darwin":
                    subprocess.Popen(["open", d])
                else:
                    subprocess.Popen(["xdg-open", d])
            except Exception as ex:
                messagebox.showerror("Dossier commande", f"Impossible d'ouvrir {d} :\n{ex}")

        def verifier_codes(self, _e=None):
            if _e is not None and _e.widget is self:
                self.actualiser(silencieux=True)    # nouvelles commandes déposées dans le dossier ?
            avant = _codes_etat["mtime"]
            codes_a_jour()
            if _codes_etat["mtime"] != avant:
                self.tout_rafraichir(sauver=False)
                self.modifie = True              # le PDF de sortie devra être mis à jour
                if _codes_etat["erreurs"]:
                    messagebox.showwarning("codes.txt", "Lignes ignorées dans codes.txt :\n\n"
                                           + "\n".join(_codes_etat["erreurs"]))
                else:
                    self.statut.config(text="Codes mis à jour. Cliquez sur « Mettre à jour le PDF » "
                                            "pour les reporter dans le PDF de sortie.")

        # ---- commandes reçues par mail ---------------------------------------------
        def _minuteur_mail(self):
            if not MAIL_DISPONIBLE:                     # version manuelle : jamais de relevé
                return
            if lire_prefs().get("mail_actif"):
                self.relever_mail_maintenant(manuel=False)
            self.after(MAIL_INTERVALLE_MS, self._minuteur_mail)

        def relever_mail_maintenant(self, manuel=True, fini=None):
            """Relève la boîte (dans un fil à part) ; les PDF trouvés vont dans « commande » puis au planning."""
            import threading
            if self._mail_en_cours:
                return
            pr = lire_prefs()
            adresse, protege = pr.get("mail_adresse", ""), pr.get("mail_mdp", "")
            exp = [e for e in pr.get("mail_expediteurs", []) if e]
            if not adresse or not protege or not exp:
                if manuel:
                    messagebox.showinfo("Commandes par mail", "Indiquez d'abord l'adresse, le mot de passe et les expéditeurs.")
                return
            self._mail_en_cours = True
            if manuel:
                self.statut.config(text="Relevé de la boîte mail…")

            def travail():
                crees, erreur = [], None
                try:
                    pieces, validite, dernier = relever_mail(
                        adresse, deproteger(protege), exp, pr.get("mail_uidvalidite", 0), pr.get("mail_dernier_uid", 0),
                        pr.get("mail_serveur") or MAIL_SERVEUR, int(pr.get("mail_port") or MAIL_PORT))
                    crees = ranger_pieces_mail(pieces)
                    ecrire_pref("mail_uidvalidite", validite)
                    ecrire_pref("mail_dernier_uid", dernier)
                except OSError as ex:
                    erreur = str(ex)
                except Exception as ex:                                     # réponse inattendue du serveur
                    erreur = f"{type(ex).__name__} : {ex}"
                self.after(0, lambda: self._resultat_mail(manuel, crees, erreur, fini))

            threading.Thread(target=travail, daemon=True).start()

        def _resultat_mail(self, manuel, crees, erreur, fini):
            self._mail_en_cours = False
            heure = dt.datetime.now().strftime("%d/%m %H:%M")
            if erreur:
                etat = f"Relevé le {heure} : échec ({erreur})"
            else:
                n = len(crees)
                etat = f"Relevé le {heure} : " + ("aucune nouvelle commande" if not n else
                                                  f"{n} nouvelle{'s' if n > 1 else ''} commande{'s' if n > 1 else ''}")
            ecrire_pref("mail_etat", etat)
            if crees:
                self.actualiser()                       # range les PDF et les ajoute au planning
                self.statut.config(text=f"{len(crees)} commande(s) reçue(s) par mail et ajoutée(s) au planning.")
            elif manuel:
                self.statut.config(text=etat)
                if erreur:
                    messagebox.showwarning("Commandes par mail", etat)
            if fini:
                fini(etat, bool(erreur))

        def fenetre_mail(self):
            """Réglages du relevé des commandes dans la boîte mail."""
            pr = lire_prefs()
            fen = tk.Toplevel(self)
            fen.title("Commandes par mail")
            fen.transient(self)
            fen.resizable(False, False)
            c = ttk.Frame(fen, padding=14)
            c.pack(fill="both", expand=True)
            ttk.Label(c, text="Commandes reçues par mail", font=("", 12, "bold")).grid(row=0, column=0, columnspan=2, sticky="w")
            ttk.Label(c, foreground="#555", justify="left", wraplength=430, text=(
                "Le programme relève la boîte mail au démarrage puis toutes les heures, et dépose dans le dossier "
                "« commande » les bulletins envoyés par les expéditeurs ci-dessous (pièces jointes PDF dont le nom contient "
                "« bulletin de commande » ou « contrairement ») : ils sont ajoutés au planning. "
                "Il ne fait que lire : aucun message n'est envoyé, effacé, déplacé ni marqué comme lu.")).grid(
                row=1, column=0, columnspan=2, sticky="w", pady=(2, 10))
            v_actif = tk.BooleanVar(value=bool(pr.get("mail_actif")))
            v_adr = tk.StringVar(value=pr.get("mail_adresse", ""))
            v_mdp = tk.StringVar(value="")
            exp = (pr.get("mail_expediteurs") or []) + ["", ""]
            v_e1, v_e2 = tk.StringVar(value=exp[0]), tk.StringVar(value=exp[1])
            v_srv = tk.StringVar(value=pr.get("mail_serveur") or MAIL_SERVEUR)
            v_port = tk.StringVar(value=str(pr.get("mail_port") or MAIL_PORT))
            ttk.Checkbutton(c, text="Relever automatiquement (au démarrage puis toutes les heures)",
                            variable=v_actif).grid(row=2, column=0, columnspan=2, sticky="w", pady=(0, 8))
            lignes = (("Adresse mail complète :", v_adr, None), ("Mot de passe de la boîte :", v_mdp, "•"),
                      ("Expéditeur des commandes 1 :", v_e1, None), ("Expéditeur des commandes 2 :", v_e2, None),
                      ("Serveur IMAP (SSL) :", v_srv, None), ("Port :", v_port, None))
            for i, (lib, var, cache) in enumerate(lignes):
                ttk.Label(c, text=lib).grid(row=3 + i, column=0, sticky="w", pady=2)
                ttk.Entry(c, textvariable=var, width=34, show=cache or "").grid(row=3 + i, column=1, sticky="w", pady=2)
            note = ttk.Label(c, foreground="#666", text=(
                "Mot de passe enregistré : laissez vide pour le garder." if pr.get("mail_mdp") else
                "Free : imap.free.fr, port 993, adresse complète et mot de passe habituel."))
            note.grid(row=9, column=0, columnspan=2, sticky="w", pady=(4, 0))
            etat = ttk.Label(c, foreground="#555", wraplength=430, text=pr.get("mail_etat", ""))
            etat.grid(row=11, column=0, columnspan=2, sticky="w", pady=(8, 0))

            def enregistrer(verifier=True):
                adr, mdp = v_adr.get().strip(), v_mdp.get()
                exps = [e.strip() for e in (v_e1.get(), v_e2.get()) if e.strip()]
                if verifier and v_actif.get():
                    if not re.match(r"^[^@\s]+@[^@\s]+\.[^@\s]+$", adr):
                        messagebox.showwarning("Commandes par mail", "Indiquez l'adresse mail complète (ex. prenom.nom@free.fr).", parent=fen)
                        return False
                    if not mdp and not lire_prefs().get("mail_mdp"):
                        messagebox.showwarning("Commandes par mail", "Indiquez le mot de passe de la boîte mail.", parent=fen)
                        return False
                    if not exps:
                        messagebox.showwarning("Commandes par mail", "Indiquez au moins un expéditeur des commandes.", parent=fen)
                        return False
                try:
                    port = int(v_port.get() or MAIL_PORT)
                except ValueError:
                    port = MAIL_PORT
                if adr.lower() != lire_prefs().get("mail_adresse", "").lower():      # autre boîte : on repart de zéro
                    ecrire_pref("mail_uidvalidite", 0)
                    ecrire_pref("mail_dernier_uid", 0)
                ecrire_pref("mail_actif", bool(v_actif.get()))
                ecrire_pref("mail_adresse", adr)
                ecrire_pref("mail_expediteurs", exps)
                ecrire_pref("mail_serveur", v_srv.get().strip() or MAIL_SERVEUR)
                ecrire_pref("mail_port", port)
                if mdp:
                    try:
                        ecrire_pref("mail_mdp", proteger(mdp))
                    except OSError as ex:
                        messagebox.showerror("Commandes par mail", str(ex), parent=fen)
                        return False
                    v_mdp.set("")
                    note.config(text="Mot de passe enregistré : laissez vide pour le garder.")
                return True

            def relever():
                actif = v_actif.get()
                v_actif.set(True)
                ok = enregistrer()
                v_actif.set(actif)
                ecrire_pref("mail_actif", bool(actif))
                if not ok:
                    return
                b_rel.state(["disabled"])
                etat.config(text="Relevé en cours…", foreground="#555")

                def fini(texte, erreur):
                    try:
                        b_rel.state(["!disabled"])
                        etat.config(text=texte, foreground="#b00020" if erreur else "#555")
                    except tk.TclError:
                        pass                                  # fenêtre fermée entre-temps
                self.relever_mail_maintenant(manuel=False, fini=fini)

            def oublier():
                ecrire_pref("mail_mdp", "")
                ecrire_pref("mail_actif", False)
                v_actif.set(False)
                note.config(text="Mot de passe oublié, relevé arrêté.")

            b = ttk.Frame(c, padding=(0, 12, 0, 0))
            b.grid(row=10, column=0, columnspan=2, sticky="we")
            ttk.Button(b, text="Enregistrer", command=lambda: enregistrer() and fen.destroy()).pack(side="left")
            b_rel = ttk.Button(b, text="Relever maintenant", command=relever)
            b_rel.pack(side="left", padx=6)
            ttk.Button(b, text="Oublier le mot de passe", command=oublier).pack(side="left")
            ttk.Button(b, text="Fermer", command=fen.destroy).pack(side="right")
            fen.bind("<Escape>", lambda e: fen.destroy())
            fen.grab_set()

        # ---- mise à jour automatique de l'exe -------------------------------------
        def verifier_maj(self, manuel=False):
            """Cherche une nouvelle PlanningPDF.exe sur GitHub (automatique : une fois par jour, en silence)."""
            import threading
            version, depot = version_build()
            fige = getattr(sys, "frozen", False) and sys.platform.startswith("win")
            if not fige or version is None:
                if manuel:
                    messagebox.showinfo("Mise à jour", (
                        "La mise à jour automatique concerne PlanningPDF.exe téléchargé depuis GitHub.\n\n"
                        + ("Ce programme a été lancé sans l'exe (planning_pdf.py)."
                           if not fige else
                           "Cet exe a été fabriqué sur ce PC (« Creer l'exe ») : il ne connaît pas votre dépôt GitHub. "
                           "Téléchargez une fois PlanningPDF.exe depuis la page « Releases » de GitHub : "
                           "les versions suivantes s'installeront ensuite toutes seules.")))
                return
            if self._maj_prete:
                if manuel:
                    messagebox.showinfo("Mise à jour", "La nouvelle version est déjà téléchargée : "
                                                       "elle s'installera à la fermeture du programme.")
                return
            aujourdhui = dt.date.today().isoformat()
            if not manuel and lire_prefs().get("maj_verifiee_le") == aujourdhui:
                return
            if manuel:
                self.statut.config(text="Recherche d'une nouvelle version…")

            def travail():
                try:
                    publiee = version_publiee(depot)
                    erreur = None
                except OSError as ex:
                    publiee, erreur = None, str(ex)
                self.after(0, lambda: self._resultat_maj(manuel, version, publiee, erreur))

            threading.Thread(target=travail, daemon=True).start()

        def _resultat_maj(self, manuel, version, publiee, erreur):
            if erreur:
                if manuel:
                    self.statut.config(text="")
                    messagebox.showwarning("Mise à jour", f"Vérification impossible : {erreur}.")
                return
            ecrire_pref("maj_verifiee_le", dt.date.today().isoformat())
            if publiee <= version:
                if manuel:
                    self.statut.config(text="")
                    messagebox.showinfo("Mise à jour", f"Vous avez la dernière version (n° {version}).")
                return
            if not messagebox.askyesno("Mise à jour disponible",
                                       f"Une nouvelle version de Planning PDF est disponible : n° {publiee} "
                                       f"(vous avez le n° {version}).\n\nLa télécharger et l'installer maintenant ?\n"
                                       "Votre planning, vos codes et vos réglages sont conservés."):
                return
            self._installer_maj(publiee)

        def _installer_maj(self, publiee):
            import subprocess
            import threading
            _, depot = version_build()
            exe = os.path.abspath(sys.executable)
            nouveau = os.path.join(os.path.dirname(exe), "PlanningPDF.nouveau.exe")

            def progression(pct):
                self.after(0, lambda: self.statut.config(
                    text=f"Téléchargement de la version {publiee}… {pct} %" if pct >= 0 else f"Téléchargement de la version {publiee}…"))

            def travail():
                try:
                    telecharger_exe(depot, nouveau, progression)
                    erreur = None
                except OSError as ex:
                    erreur = str(ex)
                self.after(0, lambda: fini(erreur))

            def fini(erreur):
                if erreur:
                    self.statut.config(text="")
                    messagebox.showerror("Mise à jour", f"Téléchargement impossible : {erreur}.")
                    return
                # PyInstaller « onefile » : deux processus (lanceur + programme) gardent l'exe ouvert
                pids = sorted({os.getpid(), os.getppid()})
                bat = os.path.join(tempfile.gettempdir(), "PlanningPDF_mise_a_jour.bat")
                try:
                    with open(bat, "w", encoding="utf-8", newline="") as f:
                        f.write(script_remplacement(exe, nouveau, pids))
                    subprocess.Popen(["cmd", "/c", bat], creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                                     close_fds=True)
                except OSError as ex:
                    messagebox.showerror("Mise à jour", f"Installation impossible : {ex}")
                    return
                self._maj_prete = True
                self.statut.config(text=f"Version {publiee} prête : elle s'installera à la fermeture du programme.")
                if messagebox.askyesno("Mise à jour", f"La version {publiee} est téléchargée.\n\n"
                                                      "Fermer le programme maintenant pour l'installer ? "
                                                      "Il se relancera tout seul."):
                    self.quitter()

            threading.Thread(target=travail, daemon=True).start()

        def quitter(self):
            # reporte les dernières modifications (notes…) dans le PDF de sortie
            if self.modifie and self.pl.sortie and os.path.exists(self.pl.sortie):
                if messagebox.askyesno("Mettre à jour le PDF",
                                       "Le planning a été modifié.\n"
                                       f"Mettre à jour « {os.path.basename(self.pl.sortie)} » avant de quitter ?"):
                    self.maj_sortie()
            self.destroy()

    App().mainloop()


if __name__ == "__main__":
    lancer_interface()
