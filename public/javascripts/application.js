jQuery(window).ready(function () {
    $.ajaxSetup({cache: true});
    refresh_links();

    $(document).on('click', "#generateResources", function () {
        generate_resources();
    });

    $(document).click(function (e) {
        if (e.target.tagName.toLowerCase() !== 'input') {
            apply_name();
        }
    });

    $(document).on('keyup', ".deck-name-edit", function (e) {
        if (e.keyCode === 13) {
            apply_name();
        }
    });

    $(document).on('keyup', "input", function (e) {
        apply_score();
    });

    $(document).on('keyup', "#casters-list", function (e) {
        $('#generate-casters-list').attr("href", "/casters?list=" + encodeURIComponent($("#casters-list").val()));
    });

    $(document).on('click', ".opponent-link", function (e) {
        var currentText = $(e.currentTarget).text();
        $("#opponents").each(function () {
            this.remove();
        });
        $.get("/opponents/info?opponents=" + currentText, function (response) {
            var generated = "";
            response.opponents.forEach(function (opponent) {
                if (opponent.deck != null) {
                    generated += '                    <td>\n' +
                        '                        <div class="deck">\n' +
                        '                           <div class="player-name">\n' +
                        '                               <p class="p-header">\n' +
                        '                                   <a target="_blank" href="/player?playerName=' + opponent.name + '">' + opponent.name + '</a>\n' +
                        '                               </p>\n' +
                        '                               <label><input class="score" type="text" placeholder="' + opponent.name + '\'s score" value="0"></label>' +
                        '                               <a link="' + opponent.deck.url + '" href="/download/tourney-left?link=' + opponent.deck.url + '&name=' + opponent.deck.name + '&player=' + opponent.name + '" class="badge badge-secondary generate-left noprint" download>Main(left)</a>\n' +
                        '                               <a link="' + opponent.deck.url + '" href="/download/tourney-left?link=' + opponent.deck.url + '&name=' + opponent.deck.name + '&player=' + opponent.name + '" class="badge badge-dark generate-right noprint" download>Handcam(right)</a>\n' +
                        '                           </div>\n' +
                        '                           <div><p class="p-header deck-name">' + opponent.deck.name + '</p></div>\n' +
                        '                           <textarea rows="20" cols="50">' + opponent.deck.list + '</textarea>\n' +
                        '                        </div>\n' +
                        '                    </td>\n';
                }
            });
            $(".container").append('<div id="opponents">\n' +
                '            <table class="table-bordered">\n' +
                '                <tr>\n' + generated +
                '                </tr>\n' +
                '            </table>\n' +
                '            <a id="generate-panel" class="btn btn-primary" href="#" download>Generate side panel</a>\n' +
                '        </div>');
            refresh_generate_side_panel_link();
        });
    });

    $(document).on('dblclick', ".deck-name", function (e) {
        apply_name();
        var currentText = $(e.currentTarget).text();
        var parent = $(e.currentTarget).parent();
        $(e.currentTarget).remove();
        $(parent).append('<input class="deck-name-edit" type="text" value="' + currentText + '">');
        $('.deck-name-edit').focus()
    });

    function apply_name() {
        var editField = $('.deck-name-edit');
        if (editField.length > 0) {
            var currentText = editField.val();
            var parent = $(editField).parent();
            var deckDiv = $(parent).parent();
            $(editField).remove();
            $(parent).append('<p class="p-header deck-name">' + currentText + '</p>');
            refresh_links();

        }
    }

    function apply_score() {
        refresh_generate_side_panel_link()
    }

    function refresh_generate_side_panel_link() {
        var players = $(".deck");
        var generatedUrl = "/download/streaming/panel?";
        var opponentId = 1;
        players.each(function () {
            var playerDiv = this;
            if (opponentId > 1) generatedUrl += "&";
            generatedUrl += "player" + opponentId + "Name=" + encodeURIComponent($(playerDiv).find(".p-header a").text())
                + "&player" + opponentId + "Score=" + encodeURIComponent($(playerDiv).find("input").val()) +
                "&player" + opponentId + "DeckName=" + encodeURIComponent($(playerDiv).find(".deck-name").text());
            opponentId += 1;
        });
        $('#generate-panel').attr("href", generatedUrl);
    }

    function refresh_links() {
        var decks = $(".deck");
        decks.each(function () {
            var deckDiv = this;
            var currentText = encodeURIComponent($(deckDiv).find('.deck-name').text());
            var deckLink = encodeURIComponent($(deckDiv).find('.generate-left').attr("link"));
            var playerName = encodeURIComponent($(deckDiv).find('.player-name').find("p").text());
            $(deckDiv).find('.generate-left').attr("href", "/download/tourney-left?link=" + deckLink + "&name=" + currentText + "&player=" + playerName);
            $(deckDiv).find('.generate-right').attr("href", "/download/tourney-right?link=" + deckLink + "&name=" + currentText + "&player=" + playerName);
        });
        refresh_generate_side_panel_link();
    }

    function generate_resources() {
        $.ajax({
            type: "POST",
            url: "/resources",
            data: JSON.stringify({
                players: $("#matches").val(),
                p1score: $("#currentScorePlayer1").val(),
                p2score: $("#currentScorePlayer2").val()
            }),
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (msg) {
                $("#generatedResources").remove();
                $(".container").append('<div id="generatedResources" class="row">' +
                    '<div class="col-sm-6"><img src="/assets/images/' + msg.left + '" style="width:100%"></div>' +
                    '<div class="col-sm-6"><img src="/assets/images/' + msg.right + '" style="width:100%"></div>' +
                    '</div>');
            }
        });
    }

    $(document).ready(
        function () {
            var eternalFormat = new RegExp('.*\\+\\d{4}');
            var discordFormat = new RegExp('.*\\#\\d{4}');
            var player = $(".player");
            var decks = player.find(".deckLinkBlock");
            var eternalNames = player.find(".eternalName");
            var discordNames = player.find(".discordName");
            decks.each(function () {
                var deck = this;
                var url = $(deck).find(".deckLink").text();
                $.get("/validateDeck?url=" + url, function (response) {
                    //redraw progress
                    if (response.valid === true) {
                        $(deck).addClass("btn-success").removeClass("btn-light")
                    } else {
                        // $(deck).addClass("btn-danger").removeClass("btn-light");
                        response.messages.forEach(function (message) {
                            $(deck).append('<span class="badge badge-pill badge-danger">' + message + '</span>');
                            var errorMessage = '<div style="color: darkred">Hello<br>' +
                                'Your Decklist for this weekends ETS is not marked as Tournament Deck<br>' +
                                'Please update your decklist in Eternal Warcry.<br>' +
                                'If you have any issues please let me know<br>' +
                                'Quick link to your list: <code><</code>' + url + '<code>></code></div>';
                            $(deck).append(errorMessage);
                        });
                    }
                });
            });
            eternalNames.each(function () {
                var name = $(this).text().trim();
                if (!eternalFormat.test(name)) {
                    $(this).append('<span class="badge badge-pill badge-danger">Not a valid eternal name</span>')
                }
            });
            discordNames.each(function () {
                var name = $(this).text().trim();
                if (!discordFormat.test(name)) {
                    $(this).append('<span class="badge badge-pill badge-danger">Not a valid discord name</span>')
                }
            });
        }
    )
});