(ns art.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.next :as om :refer-macros [defui]]
            [ajax.core :refer [GET]]
            [goog.dom :as gdom]
            [clojure.string :as s]
            [clojure.walk :refer [keywordize-keys]]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :as async :refer [put! <! chan]]
            [om.dom :as dom :include-macros true]))

(enable-console-print!)

(defn request [method url cb]
  (let [headers {"Accept" "application/json"
                 "X-Access-Token" "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1ODNmNjY0YWIyMDJhMzY0NjMwMDA3YjciLCJzYWx0X2hhc2giOiI1ZmY2MDUwMmEwZGIxNjZiMGMyZTRkNTkzODUxOGQxYyIsInJvbGVzIjoidXNlciIsInBhcnRuZXJfaWRzIjpbXSwiZXhwIjoyMjY5NDY4MzczLCJpYXQiOjE0ODA1NDk5NzMsImF1ZCI6IjUzZmYxYmNjNzc2ZjcyNDBkOTAwMDAwMCIsImlzcyI6IkdyYXZpdHkiLCJqdGkiOiI1ODNmNjY1NWNkNTMwZTY0NTcwMDA3ODMifQ.g6LlGHgDfs9tgusrNxteeP6LzUcZmy0hDoARUU2jiu0"
                 "Content-Type" "application/json"}
        handler (fn [res] (cb (keywordize-keys res)))]
    (method url {:handler handler :headers headers})))

(def dbg (atom nil))

(defn get-artwork-details [artwork]
  (let [out-c (chan)]
    (go
      (let [artist-chan (chan)
            image-link-raw (-> artwork :_links :image :href)
            image-link (s/replace image-link-raw #"\{image_version\}" "large")
            artist-link (-> artwork :_links :artists :href)
            _ (request GET artist-link #(put! artist-chan %))
            artist (-> (<! artist-chan)
                       :_embedded
                       :artists
                       (get 0)
                       (select-keys [:name :location :id]))
            artwork-props (select-keys artwork [:id :title :category])]
        (put! out-c (merge artwork-props {:image image-link
                                          :artist artist}))))
    out-c))

(defn get-artworks []
  (go
    (let [out (chan)
          _ (request GET "https://api.artsy.net/api/artworks" #(put! out %))
          res (<! out)
          artworks (-> res :_embedded :artworks)
          chan-array (map get-artwork-details artworks)]
      {:artworks (<! (async/map vector chan-array))})))

(defui Artist
  static om/Ident
  (ident [this {:keys [id]}]
    [:artists/by-id id])

  static om/IQuery
  (query [this]
    [:name :location :id])

  Object
  (render [this]
    (let [{:keys [name location]} (om/props this)]
      (dom/div nil
        (dom/h1 nil (str "By: " name))
        (dom/h2 nil (str "Location: " location))))))

(def artistt (om/factory Artist))

(defui Artwork
  static om/Ident
  (ident [this {:keys [id]}]
    [:artworks/by-id id])

  static om/IQuery
  (query [this]
    [:id :title :category :image {:artist (om/get-query Artist)}])

  Object
  (render [this]
    (let [{:keys [title category image artist]} (om/props this)]
      (dom/div nil
        (dom/h1 nil title)
        (dom/h2 nil category)
        (dom/img #js {:src image})
        (artistt artist)))))

(def artwork (om/factory Artwork))

(defui Artworks
  static om/IQuery
  (query [this]
    [{:artworks (om/get-query Artwork)}])

  Object
  (render [this]
    (let [artworks (:artworks (om/props this))]
      (apply dom/div nil
        (map #(dom/div nil
                (artwork %)
                (dom/hr nil)) artworks)))))

(defmulti readd om/dispatch)

(defmethod readd :artworks
  [{:keys [state query] :as fff} key _]
  (let [st @state]
    (if-let [val (get st key)]
      {:value (om/db->tree query val st)}
      {:artworks true})))

(defmethod readd :default
  [{:keys [state]}])

(defn sendd [{:keys [artworks]} cb]
  (when artworks
    (go
      (let [_artworks (<! (get-artworks))]
        (cb (om/tree->db artworks _artworks true))))))

(def p (om/parser {:read #'readd}))
(def r (om/reconciler {:state (atom nil)
                       :parser p
                       :send #'sendd
                       :remotes [:artworks]}))

(om/add-root! r Artworks (gdom/getElement "app"))
