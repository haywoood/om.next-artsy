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
                 "X-Access-Token" ""
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
                       (select-keys [:name :loctation :id]))
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

(defui App
  Object
  (render [this]
    (let [artworks (:artworks (om/props this))
          _ (pprint (om/props this))]
      (apply dom/div nil
        (map (fn [{:keys [image artist] :as ff}]
               (dom/img #js {:src image}))
          artworks)))))

(def app (om/factory App))

(go
  (let [artworks (<! (get-artworks))]
    (reset! dbg artworks)
    (js/ReactDOM.render (app artworks) (gdom/getElement "app"))))

(defui Artist
  static om/Ident
  (ident [this {:keys [id]}]
    [:artists/by-id id])

  static om/IQuery
  (query [this]
    [:name :loctation :id]))

(defui Artwork
  static om/Ident
  (ident [this {:keys [id]}]
    [:artworks/by-id id])

  static om/IQuery
  (query [this]
    [:id :title :category :image {:artist (om/get-query Artist)}]))

(defui Artworks
  static om/IQuery
  (query [this]
    [{:artworks (om/get-query Artwork)}]))

(comment
  (pprint (om/tree->db (om/get-query Artworks) @dbg true)))
