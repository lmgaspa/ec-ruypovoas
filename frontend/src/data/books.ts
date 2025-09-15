import { descriptions } from './descriptions';

export interface Book {
  id: string;
  title: string;
  imageUrl: string;
  price: string;
  description: string;
  author: string;
  additionalInfo: Record<string, string>;
  category: string;
  relatedBooks: { title: string; imageUrl: string; price: string; category: string; id: string }[];
  stock: number;
}

const editoraNossoLar = "Editora Nosso Lar";
const selo = "Editora Nosso Lar";
const idioma = "Português";

export const books: Book[] = [
  {
    id: "sonhos",
    title: "Sonhos não são impossíveis",
    imageUrl: "/images/sonhos.webp",
    price: "R$30,00",
    description: descriptions.sonhos,
    author: "Antônio Reinaldo Carneiro de Oliveira",
    additionalInfo: {
      Peso: "285 g",
      Dimensões: "16 × 23 × 2,3 cm",
      Selo: selo,
      ISBN: "978-85-67303-00-0",
      Edição: "1ª",
      "Ano de Publicação": "2013",
      "Nº de Páginas": "172",
      Idioma: idioma
    },
    category: "Romance Espirita",
    stock: 100,
    relatedBooks: [{ id: "julian", title: "O pequeno Julian", imageUrl: "/images/julian.jpg", price: "R$30,00", category: "Romance Espirita" }
    ]
  },      
  {
    id: "julian",
    title: "O pequeno Julian",
    imageUrl: "/images/julian.jpg",
    price: "R$30,00",
    description: descriptions.julian,
    author:  "Matrai Josef Laszlo",
    additionalInfo: {
      Peso: "396 g",
      Dimensões: "16 × 23 × 2,3 cm",
      Selo: selo,
      ISBN: "978-8567303017",
      Edição: "1ª",
      "Ano de Publicação": "2014",
      "Nº de Páginas": "199",
      Idioma: idioma
    },
    category: "Romance Espirita",
    stock: 100,
    relatedBooks: [{ id: "sonhos", title: "Sonhos não são impossíveis", imageUrl: "/images/sonhos.webp", price: "R$30,00", category: "Romance Espirita" }
    ]
  },
];
