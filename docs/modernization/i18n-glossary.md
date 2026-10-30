# i18n-glossary.md: translation glossary for the 20 locales

Committed in M5 before any translation (D-105). It is a verbatim copy of `L10N_STORE_LEGAL.md` §1.6; section references below (§1.7, §2.4) point into `L10N_STORE_LEGAL.md`. The style rules that go with it are `L10N_STORE_LEGAL.md` §1.7.

The first M5 commit creates `docs/modernization/i18n-glossary.md` as a verbatim copy of this section, before any translation exists (D-105). Translations use exactly these terms. A term changes only by editing the glossary and every string that uses it, in one commit.

**Never translate:**
- "2048", every game numeral, and "3×3" … "8×8" (Latin digits and U+00D7).
- "TalkBack", "Android", "Google", "GitHub", "Ctrl", "Ctrl+Z", and the key letters W, A, S, D, H, J, K, L, U, Z, N, R. Languages that inflect foreign names may decline them ("Androida"), keeping the base word.
- "Classic Refined" and "Neon Arcade" (product names; inside a translated sentence they stay in Latin letters).
- License names, URLs and the package name.

The key names "Arrow keys", "Backspace" and "D-pad" may be translated or kept, whichever the language's keyboards and controllers use.

**A. Pieces and actions**

| Locale | tile | move (noun) | merge (verb) | Undo (button) | undos (counted) | Score (label) |
|---|---|---|---|---|---|---|
| en | tile | move | merge | Undo | undos | Score |
| cs | dlaždice | tah | spojit se | Vrátit tah | vrácení tahu | Skóre |
| da | brik | træk | smelte sammen | Fortryd | fortrydelser | Point |
| de | Kachel | Zug | verschmelzen | Rückgängig | Rücknahmen | Punkte |
| el | πλακίδιο | κίνηση | συγχωνεύομαι | Αναίρεση | αναιρέσεις | Σκορ |
| eo | kahelo | movo | kunfandiĝi | Malfari | malfaroj | Poentoj |
| es | ficha | movimiento | combinarse | Deshacer | movimientos deshechos | Puntos |
| fa | کاشی | حرکت | ادغام شدن | واگرد | واگرد (singular after numbers) | امتیاز |
| fr | tuile | coup | fusionner | Annuler | annulations | Score |
| hu | lapka | lépés | összeolvad | Visszavonás | visszavonás (singular after numbers) | Pontszám |
| it | tessera | mossa | unirsi | Annulla | annullamenti | Punteggio |
| ko | 타일 | 이동 | 합쳐지다 | 되돌리기 | 되돌리기 N회 | 점수 |
| lt | plytelė | ėjimas | susijungti | Atšaukti ėjimą | atšaukimai | Taškai |
| nl | tegel | zet | samenvoegen | Ongedaan maken | keer ongedaan gemaakt | Score |
| pl | kafelek | ruch | łączyć się | Cofnij | cofnięcia | Wynik |
| pt | peça | jogada | juntar-se | Desfazer | jogadas desfeitas | Pontuação |
| ru | плитка | ход | объединяться | Отменить ход | отмены хода | Счёт |
| sl | ploščica | poteza | združiti se | Razveljavi | razveljavitve | Rezultat |
| sr | плочица | потез | спојити се | Опозови | опозивања | Резултат |
| tr | karo | hamle | birleşmek | Geri al | geri alma (singular after numbers) | Puan |
| uk | плитка | хід | об’єднуватися | Скасувати хід | скасування | Рахунок |

**B. Records and game flow**

| Locale | Best (label) | New best | Best without undo | New game | Keep going | Game over | You win! |
|---|---|---|---|---|---|---|---|
| en | Best | New best | Best without undo | New game | Keep going | Game over | You win! |
| cs | Nejlepší | Nový rekord | Nejlepší bez vracení | Nová hra | Hrát dál | Konec hry | Vyhráli jste! |
| da | Bedste | Ny rekord | Bedste uden fortryd | Nyt spil | Fortsæt | Spillet er slut | Du vandt! |
| de | Rekord | Neuer Rekord | Rekord ohne Rücknahme | Neues Spiel | Weiterspielen | Spiel vorbei | Gewonnen! |
| el | Ρεκόρ | Νέο ρεκόρ | Ρεκόρ χωρίς αναίρεση | Νέο παιχνίδι | Συνέχεια | Τέλος παιχνιδιού | Κερδίσατε! |
| eo | Rekordo | Nova rekordo | Rekordo sen malfaro | Nova ludo | Daŭrigi | Ludo finiĝis | Vi venkis! |
| es | Récord | Nuevo récord | Récord sin deshacer | Nueva partida | Seguir jugando | Fin de la partida | ¡Has ganado! |
| fa | رکورد | رکورد جدید | رکورد بدون واگرد | بازی جدید | ادامهٔ بازی | پایان بازی | برنده شدید! |
| fr | Record | Record battu | Record sans annulation | Nouvelle partie | Continuer | Partie terminée | Vous avez gagné ! |
| hu | Rekord | Új rekord | Rekord visszavonás nélkül | Új játék | Folytatás | Vége a játéknak | Győzelem! |
| it | Record | Nuovo record | Record senza annullamenti | Nuova partita | Continua | Partita finita | Hai vinto! |
| ko | 최고 | 신기록 | 되돌리기 없는 최고 점수 | 새 게임 | 계속하기 | 게임 오버 | 승리! |
| lt | Rekordas | Rekordas! | Rekordas be atšaukimų | Naujas žaidimas | Tęsti | Žaidimas baigtas | Laimėjote! |
| nl | Record | Nieuw record | Record zonder ongedaan maken | Nieuw spel | Doorspelen | Spel voorbij | Je hebt gewonnen! |
| pl | Rekord | Nowy rekord | Rekord bez cofania | Nowa gra | Graj dalej | Koniec gry | Wygrana! |
| pt | Recorde | Novo recorde | Recorde sem desfazer | Novo jogo | Continuar | Fim de jogo | Ganhou! |
| ru | Рекорд | Новый рекорд | Рекорд без отмен | Новая игра | Продолжайте | Игра закончена | Вы победили! |
| sl | Rekord | Nov rekord | Rekord brez razveljavitev | Nova igra | Nadaljuj | Konec igre | Zmagali ste! |
| sr | Рекорд | Нови рекорд | Рекорд без опозивања | Нова игра | Настави | Крај игре | Победили сте! |
| tr | Rekor | Yeni rekor | Geri almasız rekor | Yeni oyun | Devam et | Oyun bitti | Kazandınız! |
| uk | Рекорд | Новий рекорд | Рекорд без скасувань | Нова гра | Продовжуйте | Гра завершена | Ви виграли! |

**C. Board and settings**

| Locale | board | Board size | Immersive mode (seed) | Haptics | swipe (instruction) |
|---|---|---|---|---|---|
| en | board | Board size | Immersive mode | Haptics | Swipe |
| cs | hrací plocha | Velikost plochy | Celá obrazovka | Haptická odezva | Přejeďte prstem |
| da | bræt | Brætstørrelse | Fuld skærm | Haptisk feedback | Stryg |
| de | Spielfeld | Spielfeldgröße | Vollbild | Haptisches Feedback | Wische |
| el | ταμπλό | Μέγεθος ταμπλό | Πλήρης οθόνη | Απτική απόκριση | Σύρετε |
| eo | tabulo | Grandeco de la tabulo | Tutekrana reĝimo | Haptika reago | Ŝovu |
| es | tablero | Tamaño del tablero | Pantalla completa | Respuesta háptica | Desliza |
| fa | صفحهٔ بازی | اندازهٔ صفحه | تمام‌صفحه | بازخورد لمسی | بکشید |
| fr | grille | Taille de la grille | Plein écran | Retour haptique | Balayez |
| hu | tábla | Táblaméret | Teljes képernyő | Haptikus visszajelzés | Csúsztassa |
| it | griglia | Dimensione griglia | Schermo intero | Feedback aptico | Scorri |
| ko | 보드 | 보드 크기 | 전체 화면 | 햅틱 피드백 | 스와이프하세요 |
| lt | lenta | Lentos dydis | Visas ekranas | Haptinis atsakas | Braukite |
| nl | bord | Bordgrootte | Volledig scherm | Haptische feedback | Veeg |
| pl | plansza | Rozmiar planszy | Pełny ekran | Reakcja haptyczna | Przesuń |
| pt | tabuleiro | Tamanho do tabuleiro | Tela cheia | Resposta tátil | Deslize |
| ru | поле | Размер поля | Полноэкранный режим | Тактильный отклик | Проведите |
| sl | plošča | Velikost plošče | Celozaslonski način | Haptični odziv | Povlecite |
| sr | табла | Величина табле | Цео екран | Хаптички одзив | Превуците |
| tr | tahta | Tahta boyutu | Tam ekran | Dokunsal geri bildirim | Kaydırın |
| uk | поле | Розмір поля | Повноекранний режим | Тактильний відгук | Проведіть |

Notes:
- **Immersive mode** values are the research/01 §8.1 seeds (D-101), except fa, which adds the zero-width non-joiner U+200C ("تمام‌صفحه"). The seeds were normalized by research and are accepted as glossary terms.
- **ru and uk** keep the legacy web wording for Undo, New game, Keep going, Game over and You win! (§1.2).
- **fr and it:** Android uses "Annuler"/"Annulla" for both Undo and Cancel. They never share a dialog here, so both follow the platform. The How to play heading `htp_undo` uses the noun ("Annulation", "Annullamento").
- **Best without undo** is the name of a statistic. Write it identically in `stats_best_no_undo`, `undo_hint_no_undo_best` and `htp_undo_body` (inflected only where grammar forces it).
- **pt** is shared by Brazil and Portugal (§1.7). "Tela cheia" is the legacy Brazilian seed; the pt-PT store listing uses "ecrã" in its own text (§2.4).
- **Changed during M5 (D-259):** fr and lt "New best" are "Record battu" and "Rekordas!", so the chip label stays within the 12 characters of §1.7 rule 7 ("Nouveau record" and "Naujas rekordas" were longer); fa "Keep going" is "ادامهٔ بازی", with the ezafe written as in the other fa compounds.
