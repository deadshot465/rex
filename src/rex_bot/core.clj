(ns rex-bot.core
  (:require [clojure.edn :as edn]
            [clojure.core.async :refer [chan close!]]
            [discljord.messaging :as discord-rest]
            [discljord.connections :as discord-ws]
            [discljord.formatting :refer [mention-user]]
            [discljord.events :refer [message-pump!]]))

(def state (atom nil))

(def bot-id (atom nil))

(def config (edn/read-string (slurp "config.edn")))

(defmulti handle-event (fn [type _data] type))

(defn random-response [user]
  (str (rand-nth (:responses config)) ", " (mention-user user) \!))

(defmethod handle-event :message-create
  [_ {:keys [channel-id author mentions content] :as _data}]
  (when (some #{@bot-id} (map :id mentions))
    (discord-rest/create-message! (:rest @state) channel-id :content (random-response author)))
  (if (= content "r?ping")
    (discord-rest/create-message! (:rest @state) channel-id :content "Pong!"))
  (if (= content "r?about")
    (discord-rest/create-message! (:rest @state) channel-id :embed
                                  {:color 0x37FEAB
                                   :description "Rex in the Church of Minamoto Kou.\nRex was inspired by the game Xenoblade Chronicles 2 on Nintendo Switch.\nRex version 0.1 was made and developed by:\n**Tetsuki Syu#1250, Kirito#9286**\nWritten with:\nClojure and [Discljord](https://github.com/IGJoshua/discljord) library."
                                   :footer {:text "Rex Bot: Release 0.1 | 2021-02-21"}
                                   :thumbnail {:url "https://cdn.discordapp.com/attachments/811517007446671391/812763072699564102/1024px-Clojure_logo.png"}
                                   :author {:name "Rex from Xenoblade Chronicles 2"
                                            :icon_url "https://cdn.discordapp.com/avatars/810723604672675870/23d0596f800ee9a20527a84316b34f09.webp?size=1024"}})))

(defmethod handle-event :ready
  [_ _]
  (discord-ws/status-update! (:gateway @state) :activity (discord-ws/create-activity :name (:playing config))))

(defmethod handle-event :default [_ _])

(defn start-bot! [token & intents]
  (let [event-channel (chan 100)
        gateway-connection (discord-ws/connect-bot! token event-channel :intents (set intents))
        rest-connection (discord-rest/start-connection! token)]
    {:events  event-channel
     :gateway gateway-connection
     :rest    rest-connection}))

(defn stop-bot! [{:keys [rest gateway events] :as _state}]
  (discord-rest/stop-connection! rest)
  (discord-ws/disconnect-bot! gateway)
  (close! events))

(defn -main [& args]
  (reset! state (start-bot! (:token config) :guild-messages))
  (reset! bot-id (:id @(discord-rest/get-current-user! (:rest @state))))
  (try
    (message-pump! (:events @state) handle-event)
    (finally (stop-bot! @state))))

