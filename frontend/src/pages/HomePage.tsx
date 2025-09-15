import Container from '../components/common/Container';
import ContentBlock from '../components/common/ContentBlock';
import { useNavigate } from 'react-router-dom';

function HomePage() {
  const navigate = useNavigate();

  return (
    <Container>
      <ContentBlock 
        title="Editora Nosso Lar"
        imageUrl="/images/editora.webp"
        description="A Editora Nosso Lar é uma empresa de livros espiritas fundadas em 2013. Recentemente, atualizamos nosso ecomercce para melhor atender os nossos clientes."
        isAuthor
      />

      <h2 className="text-4xl font-extrabold text-primary mb-16 text-center">Livros</h2>
      <div className="flex flex-wrap justify-center gap-16">
        <div onClick={() => navigate('/books/sonhos')}>
          <ContentBlock 
            title="Sonhos não são impossíveis" 
            imageUrl="/images/sonhos.webp" 
            description="Esta obra resgata os verdadeiros valores do Espiritismo em suas dimensões mais profundas,
            trazendo ainda temas como:
            mediunidade e obsessão, inimigos de vidas passadas, regressão
            de memória, o poder invencível da fé, a força soberana da prece e nos mostra,
            como poucos livros até então, a grande vantagem
            de confiarmos em Deus e em sua soberana justiça.<br><br>

          Jamais desista daquilo que seu coração deseja, pois nenhum sonho é impossível
          quando nos entregamos à vida com confiança e coragem." 
          />
        </div>
        <div onClick={() => navigate('/books/julian')}>
          <ContentBlock 
            title="O pequeno Julian" 
            imageUrl="/images/julian.jpg" 
            description="Uma história linda onde o pequeno Julian vivencia muitos ensinamentos espirituais depois de descarnar." 
          />
        </div>
      </div>
    </Container>
  );
}

export default HomePage;
